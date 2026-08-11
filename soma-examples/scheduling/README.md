# FJSP scheduling reference application

这是一个按真实项目边界组织的 Flexible Job Shop Scheduling Problem（FJSP）reference
application，不是只展示几个 API 的片段式 demo。它使用 SOMA（State-Oriented Memory
Architecture）建模求解过程中的运行时状态，并以确定性的 machine-first FCFS + SPT 构造式规则
完成完整排程。

默认配置是常用的标准规模：

```text
1,000 Jobs
100 Operations / Job
100 Machines
3 candidate Machines / Operation
100,000 Operations
300,000 Processing Options
```

所有业务 ID 与时间值使用 `long`；FJSP 中后继工序在前序完成时直接变为 READY，因此模型没有
`releaseTime`，只有由前序完成时间决定的 `readyTime`。

## 1. Project topology

```text
config/
  fjsp-standard.properties          可调整的标准输入规模与随机分布
src/main/java/.../scheduling/
  application/                      可运行入口
  configuration/                    model factory 配置
  factory/                          确定性测试数据工厂
  modeling/                         immutable OOP input model 与校验
  runtime/schema/                   SOMA runtime schema declarations
  solver/api/                       Solver、配置、状态与结果查询合同
  solver/core/                      唯一 FCFS + SPT 实现
  validation/                       独立结果可行性校验
src/test/java/.../scheduling/       配置、factory 与 solver 行为测试
```

application 层的 `SchedModel` 是不可变问题定义；SOMA Table 只拥有算法求解期间会变化或需要高效
访问的状态。没有引入 Lombok 或其他 production dependency，所有 value object 都是显式 Java 8
immutable object。

## 2. Runtime state model

同一个 generated `SomaGroup` 中有四张 Table：

| Table | 职责 | 主要访问路径 |
|---|---|---|
| `MachineState` | 机器可用时间、已排工序与当前等待数 | Key point update；100 台机器上的 typed top |
| `JobState` | Job 完成进度与完成时间 | Key point update |
| `OperationState` | 唯一权威的工序状态与最终排程结果 | Key；`jobId` Index；结果期 machine typed filter |
| `MachineWaitingOperation` | READY 工序在候选机器队列中的派生 dispatch state | Key；`machineId` / `operationId` Index |

`OperationState.readyTime` 是权威事实；waiting row 复制该值只是为了 FCFS 排序。一个 Operation
被调度后，它在所有候选机器上的 waiting rows 都通过自然的 point `remove` 删除；后继变为 READY
后，再为其候选机器执行自然的 point `add`。example 不使用固定槽位、tombstone、application
`PriorityQueue` 或其他补偿性 frontier。

Index 只服务真实访问方向。特别是 `assignedMachineId` 在求解前大量为零且每条工序只更新一次，
同时 `byMachine` 仅用于最终结果查询，因此它保持普通 Field，并在结果期使用 typed filter；这避免
为低选择性、高变更字段维护无收益的 Index。

## 3. Algorithm flow

每次 dispatch 是一个清晰的顺序流程：

```text
MachineState(waitingOperationCount > 0)
    -> minimum availableTime, machineId
        -> MachineWaitingOperation.byMachineId(machineId)
            -> minimum readyTime (FCFS)
            -> minimum processingTime (SPT)
            -> stable business-ID tie breakers
                -> remove all candidate waiting rows
                -> update MachineState
                -> publish OperationState result
                -> update JobState
                -> release successor and add its waiting rows
```

SOMA 只保证一次 Table-local mutation 的原子性；上述跨 Table 发布顺序由 solver application
拥有。同一个 `SomaGroup` 始终由本求解线程串行使用，SOMA 内部的 optimizer 或 parallel kernel
不会改变顺序执行语义。

## 4. Public solver surface

```java
SchedModel model = new StandardSchedModelFactory().create(factoryConfig);
SchedSolveResult result = new SchedSolverImpl().solve(
        model,
        SchedSolverConfig.defaults());

Optional<OperationResult> operation = result.operations().find(operationId);
List<OperationResult> job = result.operations().byJob(jobId);
List<OperationResult> machine = result.operations().byMachine(machineId);
```

`SchedResultValidator` 独立检查工序前后约束、候选机器、加工时长、机器不重叠、结果完整性与
makespan。默认 reference journey 同时启用输入和结果校验。

## 5. Build and run

从 repository root 执行：

```sh
mvn clean install -DskipTests -Dmaven.install.skip=false
mvn -f soma-examples/scheduling/pom.xml clean test
java -Xms128m -Xmx1g \
  -cp 'soma-examples/scheduling/target/classes:soma-runtime/target/soma-runtime-1.0.0-SNAPSHOT.jar' \
  io.github.somaruntime.examples.scheduling.application.SchedulingMain
```

也可以把自定义 `.properties` 路径作为第一个参数。成功输出包含：

```text
scheduling-reference: PASS
operations=100000 makespan=... elapsedMs=...
```

固定主机的完成时间只是治理证据，不是跨硬件 SLA。当前架构、正确性和性能结论由
[Scheduling reference governance](../../project/conformance/v1-scheduling-reference-application-governance.md)
记录。

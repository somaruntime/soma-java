# SOMA reference applications

这里不是片段式 demo，而是三个按真实 application 边界组织、可独立编译运行的 reference
project。它们只使用 SOMA public/generated Java 8 API，并展示 application 应当拥有的业务编排与
补偿边界：

| Project | 重点 |
|---|---|
| [`scheduling`](scheduling/README.md) | 标准 100K-operation FJSP、四张 runtime Table、FCFS + SPT 与自然增量 waiting mutation |
| [`simulation`](simulation/README.md) | Grassing空间个体仿真、两张runtime Table、五阶段tick、UI/headless与确定性验证 |
| [`real-time-dispatch`](real-time-dispatch/README.md) | Index 缩窄、typed Join、显式并行与外部派工边界 |

三个 project 都保持 schema 与 application responsibility 分离，但不会为了目录整齐强迫不同规模的
应用使用同一层级。较完整的 scheduling project 进一步拆出 modeling、factory、configuration、
solver API/core 与 validation：

```text
src/main/java/.../schema/        schema declaration
src/main/java/.../application/   service、decision、业务协议与 Main
scheduling/.../modeling/         immutable FJSP input model
scheduling/.../runtime/schema/   solver runtime state schema
scheduling/.../solver/           public solver contract and core
```

Example 自身只保留可阅读、可运行的 application。规模测量、profiler 和跨实现正确性对照由独立的
[`benchmarks/`](../benchmarks/README.md) 工程承载，避免把开发期测量职责混入用户示例。

从 repository root 构建：

```sh
mvn clean install -Dmaven.install.skip=false -DskipTests
mvn -f soma-examples/pom.xml clean package
```

运行一个场景：

```sh
java -cp 'soma-examples/scheduling/target/classes:soma-runtime/target/*' \
  io.github.somaruntime.examples.scheduling.application.SchedulingMain
```

预期输出：

```text
scheduling-reference: PASS
```

三个 project 不进入 SOMA 的两项 production artifact，也不拥有新的产品语义。完整资格和 benchmark
分别由 [`scripts/qualify.sh`](https://github.com/somaruntime/soma-java/blob/develop/scripts/qualify.sh)
与 [`scripts/benchmark.sh`](https://github.com/somaruntime/soma-java/blob/develop/scripts/benchmark.sh)
统一管理。

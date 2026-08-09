# Scheduling reference application

该项目把 `Job`、`MachineState` 与 `ProcessingOption` 建模为同一 `SomaGroup` 中的普通 Table。
Option 通过 endpoint ID 和双向 Index 表达关系；application 负责候选评分、跨 Table 发布顺序和
补偿。

```text
reserve/add
    -> typed Join and predicate pushdown
        -> detached SchedulingDecision
            -> Machine point update
                -> Job point update or application compensation
```

它刻意不把两次 Table-local mutation 描述成跨 Table transaction：第二步失败时，
`SchedulingService` 使用保存的 detached state 恢复 Machine。

从 repository root 运行：

```sh
mvn clean install -Dmaven.install.skip=false -DskipTests
mvn -f soma-examples/scheduling/pom.xml clean package
java -cp 'soma-examples/scheduling/target/classes:soma-runtime/target/*' \
  io.github.somaruntime.examples.scheduling.application.SchedulingMain
```

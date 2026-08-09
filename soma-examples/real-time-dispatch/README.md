# Real-time dispatch reference application

该项目把 pending request、eligible relation 与 machine runtime state 保存在同一 Group。查询先用
Index 与 typed predicate 缩窄输入，再显式 `parallel()` 计算稳定评分并返回 detached decision。

外部 dispatch effect 和后续 Table-local mutation 由 application protocol 编排；SOMA 不承担外部
事务，也不会让 executor completion order 改变逻辑结果顺序。

从 repository root 运行：

```sh
mvn clean install -Dmaven.install.skip=false -DskipTests
mvn -f soma-examples/real-time-dispatch/pom.xml clean package
java -cp 'soma-examples/real-time-dispatch/target/classes:soma-runtime/target/*' \
  io.github.somaruntime.examples.realtimedispatch.application.RealTimeDispatchMain
```

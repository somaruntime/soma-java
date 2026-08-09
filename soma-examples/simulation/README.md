# Simulation reference application

`Event` Table 不拥有隐式业务顺序。Application 每一步都按
`eventMinute -> priority -> eventId` 显式排序，先取得 detached Event，再更新 EntityState，最后
删除已消费 Event。两个 Table 的发布是两个独立原子操作；后一步异常时，补偿协议由
application 拥有。

该场景主要展示稳定排序、`findFirst()`、detached decision、point update 和 remove 的正确组合，
而不是把仿真状态机塞进 SOMA runtime。

从 repository root 运行：

```sh
mvn clean install -Dmaven.install.skip=false -DskipTests
mvn -f soma-examples/simulation/pom.xml clean package
java -cp 'soma-examples/simulation/target/classes:soma-runtime/target/*' \
  io.github.somaruntime.examples.simulation.application.SimulationMain
```

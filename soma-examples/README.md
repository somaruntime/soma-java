# soma-examples

`soma-examples` 是两个独立 Java 8 参考应用的 Maven 聚合边界，不产出供其他模块依赖的领域共享 JAR：

- [`industrial-dynamic-scheduler`](industrial-dynamic-scheduler/docs/README.md)；
- [`grassing-individual-simulation`](grassing-individual-simulation/docs/README.md)。

两个应用分别拥有自己的 Blueprint、Design、配置、detached input generator、runtime state、正确性验证和 integrated performance evidence。它们只消费 SOMA public artifacts，不拥有或重新定义 SOMA 产品语义。

聚合与验证入口见 [developer docs](docs/README.md)。模块 README 只负责导航。

# soma-examples

`soma-examples` 是三个独立 Java 8 参考应用的 Maven 聚合边界，不产出供其他模块依赖的领域共享 JAR：

- [`industrial-dynamic-scheduler`](industrial-dynamic-scheduler/README.md)；
- [`grassing-individual-simulation`](grassing-individual-simulation/README.md)；
- [`real-time-dispatch-rule-engine`](real-time-dispatch-rule-engine/README.md)。

三个应用分别从自己的 business model 推导 Blueprint、Design、配置、detached
input generator、runtime state、正确性验证和 integrated performance evidence。
它们彼此不共享领域模型、fixture 或 baseline，只消费 SOMA public artifacts，
不拥有或重新定义 SOMA 产品语义。

聚合与验证入口见 [developer docs](../project/modules/soma-examples/README.md)。模块 README 只负责导航。

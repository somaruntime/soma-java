# soma-examples 参考应用入口

类型：Report / 开发者文档入口

状态：当前

Owner：SOMA Java reference applications

受众：SOMA Java application developer

适用版本：`soma-java` `0.2.0-SNAPSHOT`、两个应用 `1.0.0-SNAPSHOT`

输入事实源：两个 child application 的 POM、source、versioned config、validation 与 canonical Gate

事实范围：当前两个独立参考应用、构建隔离和应用自有 evidence 的导航

非事实范围：SOMA 核心 Design、公共 API、跨环境性能 claim 和 release readiness

最后审查日期：2026-07-23

`soma-examples` 只聚合参考应用，不再拥有一个共享领域 runtime、场景套件或产品能力事实。SOMA 的目标和长期语义分别由[产品 Blueprint](../../docs/blueprints/soma-java-product-blueprint.md)和[Design](../../docs/design/README.md)拥有；当前代码与 Gate 从[参考应用与 benchmark Map](../../docs/implementation-map/scenario-and-benchmark-map.md)进入。

## 当前参考应用

- [工业动态调度引擎](../industrial-dynamic-scheduler/docs/README.md)：flexible machine、动态事件、约束传播、增量 frontier、完整 validator 与多 fork evidence；
- [个体生态仿真](../grassing-individual-simulation/docs/README.md)：grasser–grass systems、确定性随机、AoS oracle、物理顺序独立性与 long-run evidence。

每个应用都把版本化配置和 detached input generation 与 authoritative runtime state 分开。Generator 不持有 SOMA runtime，bootstrap 校验后一次装载，hot loop 不反向调用 generator；相同配置与 seed 必须产生相同 input checksum。

## Canonical Gate

```sh
./scripts/check-reference-applications.sh
./scripts/check-industrial-scheduler.sh
./scripts/check-grassing-simulation.sh
```

第一个 Gate 在 evidence-local Maven repository 中证明两个 child 是普通 consumer；后两个 Gate 分别拥有领域 correctness、long-run 和 integrated multi-fork evidence。所有本机性能 artifact 均保持 `claimAllowed=false`。

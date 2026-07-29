# soma-examples 参考应用入口

类型：Report / 开发者文档入口

状态：当前

Owner：SOMA Java reference applications

受众：SOMA Java application developer

适用版本：`soma-java` `1.0.0`、三个应用 `1.0.0-SNAPSHOT`

输入事实源：三个 child application 的 POM、source、versioned config、validation 与 canonical Gate

事实范围：当前三个独立参考应用、构建隔离和应用自有 evidence 的导航

非事实范围：SOMA 核心 Design、公共 API、跨环境性能 claim 和 release readiness

最后审查日期：2026-07-29

`soma-examples` 只聚合参考应用，不再拥有一个共享领域 runtime、场景套件或产品能力事实。SOMA 的目标和长期语义分别由[产品 Blueprint](../../docs/blueprints/soma-java-product-blueprint.md)和[Design](../../docs/design/README.md)拥有；当前代码与 Gate 从[参考应用与 benchmark Map](../../docs/implementation-map/scenario-and-benchmark-map.md)进入。

## 当前参考应用

- [工业动态调度引擎](../industrial-dynamic-scheduler/docs/README.md)：top-level
  Problem/Factory、canonical Solver/Session、detached Result、动态约束、增量
  frontier、application-owned 单遍 assignment summary、production/test 隔离与多 fork
  evidence；
- [个体生态仿真](../grassing-individual-simulation/docs/README.md)：grasser–grass
  systems、确定性随机、AoS oracle、物理顺序独立性、fail-stop lifecycle 与
  long-run evidence；
- [实时派工规则引擎](../real-time-dispatch-rule-engine/docs/README.md)：detached
  snapshot/delta、reusable multi-source DataFlow、Join/GroupBy、受控并行、
  detached command、应用自有提交与独立 reference。

每个应用都把版本化配置和 detached input generation 与 authoritative runtime
state 分开。工业调度应用通过 Factory/Solver facade 隐藏 runtime lifecycle，
生态仿真通过 Simulator/Session 管理 tick aggregate，RTD 通过 Dispatcher 管理
有限 horizon。三个应用的运行期都不反向调用输入生成器，相同配置与 seed 必须
产生相同 input checksum。

最终设计审计确认三个应用唯一需要治理的共同偏差是相关root Table的lifecycle
owner仍由手工创建/逆序释放表达。现在三个runtime分别拥有稳定冻结的显式
SomaGroup，partial-create、fault cleanup和release由Group收口，并通过detached
Group Runtime Metadata进入evidence；领域模型、算法和Result没有装饰性改写。

三个 child 的 test source 各自保留小型 `BenchmarkEnvironment`、
`BenchmarkOptions` 与 `JvmMetrics`。这些 mechanics 形状相似，但合并会引入第四个
example test artifact 或跨 child source dependency，削弱“普通 consumer 独立性”
证据；因此不建立共享 example runtime/testkit。只有通用协议由 SOMA production
module 或领域中性 benchmark Owner 提供。

2026-07-29 logical/execution cutover后再次审计三个consumer：工业调度与生态仿真
继续使用canonical direct/exact/Candidate与显式Group；RTD继续使用generated enum
exact source、primitive typed expression、reusable Definition/Template及bounded
Join/Group。三者都没有raw logical `LongExpression`、旧protocol adapter、
Iterator/lazy pull、generic object storage或临时API，因此本轮无需装饰性production
改写。最终`1.0.0` clean candidate已由本轮唯一canonical Full重新生成、编译并
运行三个reference applications；该结论不从旧candidate自动继承。

## 从三个应用归纳的性能用法

- SOMA Table保存authoritative runtime state；跨轮次候选、优先队列和resource
  calendar等可重建算法状态使用application-owned primitive结构，不把它们伪装成
  通用Table事实。
- ColumnView适合在一个明确lifecycle内连续读取，但调用者仍应在machine group、
  resource group等自然边界提升不变读取；不要在每个candidate上重复读取同一row
  的version、family或availability。
- 动态cardinality update应复用generated primitive scratch，并同时规划
  `maximumUpdateScratchBytes`和预期high-water。评估时必须把retained scratch与
  transient allocation分开；为省少量留存而逐步精确扩容通常会放大数组复制和GC。
- DataFlow的Definition、Template、Context按生命周期复用；遇到stable sort、
  ordered merge等语义barrier时先测量barrier占比和worker状态，不能只因为存在
  parallel入口就假定整个调用可并行加速。
- 优化证据至少保持相同input/result/schema/runtime-plan identity，并同时观察
  CPU、allocation、GC和SOMA high-water。单次更快但checksum、failure或lifecycle
  变化不构成优化。

## Canonical Gate

```sh
./scripts/check-reference-applications.sh
./scripts/check-industrial-scheduler.sh
./scripts/check-grassing-simulation.sh
./scripts/check-real-time-dispatch-rule-engine.sh
./scripts/check-reference-application-performance.sh fast
./scripts/check-reference-application-performance.sh scale
./scripts/check-reference-application-performance.sh soak
./scripts/check-reference-application-performance.sh full
```

前四个 Gate 证明三个 child 是相互独立的普通 consumer，并分别拥有领域
correctness、架构、lifecycle 与 resource ownership；Fast、Scale、Soak 对应
default、large、long-run 的 3-fork performance baseline，Full 组合九个 workload
与三层结构检查。所有本机性能 artifact 均保持 `claimAllowed=false`。

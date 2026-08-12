# SOMA Grassing 空间个体仿真 Reference Application 与性能治理

类型：Conformance Entry

状态：`PASS / COMPLETED / TEMPORARY_RETIRED`

Owner：Grassing Reference Application 的 executable fact、正确性、headless/UI、性能资格与
claim boundary

最后审查日期：2026-08-12

## 1. 治理结论

旧的 three-event 演示已由完整 Grassing 空间个体仿真替换。当前 Example 是一个可配置、可测试、
可视化且能在真实 headless 环境运行的 Java 8 application，不是片段式 demo：

- `GrassCellState` 与 `GrasserState` 两张 SOMA Table 是唯一 authoritative runtime state；
- 每个 tick 固定执行 grass growth、metabolism/death、reproduction、grazing、searching/movement；
- application 拥有跨 Table protocol 与 deterministic RNG order，SOMA 拥有 Table-local
  query、Index、GroupBy、Join 与 atomic mutation；
- UI 只读取 detached primitive snapshot；headless 不创建 Window、Toolkit、EDT、snapshot 或 pacing
  sleep；
- 旧 `simulation` Narrow benchmark 保持原 identity 与 workload，新业务 journey 使用独立
  `simulation-application` identity。

本专题没有修改 SOMA production runtime/processor、public/generated API、正式 Blueprint/Design、
artifact topology 或 dependency baseline。

## 2. 交付形态

Example 的唯一用户入口是
[`soma-examples/simulation/README.md`](../../soma-examples/simulation/README.md)。实现按责任分为：

```text
configuration       strict immutable .properties input
engine/api           synchronous engine + detached results
engine/core          initialization, SOMA ownership, fixed process schedule
runtime/schema       two generated Table declarations
presentation         console + optional Swing projection
validation           final result invariant validation
src/test             model, determinism, absence and renderer evidence
```

没有引入 ECS framework、generic process SPI、event bus、DI container、application shadow arrays 或
新的 production dependency。行为二态使用 primitive `boolean searching`；空种群 energy summary 使用
`OptionalDouble.empty()`，不以零值或 NaN 冒充 absence。

## 3. 正确性证据

以下证据已通过：

- fixed seed、stable grasser ID order 与重复运行 fingerprint 等价；
- grass range、live energy、population balance、grazing/searching partition；
- grasser `cellId` Index、occupied-cell GroupBy 与 Grass/Grasser Equality Join；
- packed remove 后的完整场景与 deterministic fingerprint；
- detached snapshot 不被后续 tick 改写；
- empty population 返回明确 absent energy statistics；
- unknown config property fail closed；
- off-screen renderer 在 `java.awt.headless=true` 下工作；
- 10、100、1000 tick headless journey 均为 `simulation-reference: PASS`。

`./scripts/check.sh` 已完整通过。其覆盖 runtime 71 tests、processor 34 tests、三个 Example、
source/package qualification、Java 8 独立 generated consumer、checksum、SBOM 与 provenance。
`scripts/qualify.sh` 现在还会从 packaged runtime/processor 重新生成 simulation API、独立编译全部
application source，并在独立 JVM 中运行 headless smoke；source bundle 明确包含
`simulation/src/main` 和 `config/grassing.properties`。

## 4. Profile 与 Application 优化

固定主机是 Apple Silicon 笔记本、Java `1.8.0_502`、Parallel GC；这些数字是同机治理证据，
不是跨硬件 SLA。canonical workload 为 `100 x 100` cells、1,000 initial grassers、100 ticks、
AUTO compression；stress A/B 为 40,000 cells、4,000 initial grassers、100 ticks。

JFR 的 baseline allocation profile 显示：

- 主要 large allocation 是 Selection update/remove candidate path 中的 primitive leaf clone；
- 其次是三次按 `grasserId` 的 detached row-array sort/materialization；
- UI、render、sleep 不在 headless kernel 内。

Application 层 accepted candidate 将 reproduction、grazing、searching 的
`GrasserState[]` materialization 改成 primitive `int[]` ID projection/sort，再按 Key point get。
它不增加 shadow state，不改变 process/RNG/encounter order，default 与 stress fingerprint 均完全一致。

| Workload | Before median | After median | Improvement |
|---|---:|---:|---:|
| 10K cells / 1K grassers / 100 ticks | 428.025 ms | 388.672 ms | 9.2% |
| 40K cells / 4K grassers / 100 ticks | 1,408.325 ms | 1,140.163 ms | 19.0% |

增加累计 phase timing 后的最终 3-run median 为 368.803 ms；其中 growth 54.974 ms、metabolism
81.095 ms、reproduction 34.435 ms、grazing 88.388 ms、searching 110.513 ms。此前独立最终 JFR
run 为 383.943 ms；1000-tick headless run 约 1.02 s kernel、fingerprint/validation
`PASS`。最终 JFR 的 outside-TLAB sample 从 baseline 的 318 次 `float[]` clone、104 次 `int[]`
clone 降至 167 次和 38 次。这些是采样证据，不外推为完整 allocation byte accounting。

相同 default workload 的 AUTO/OFF 3-run median 分别约 368.8/363.2 ms，fingerprint一致；差异约
1.5%，不能据此宣称某个压缩策略显著更快。它说明该模型的频繁 touched Chunk 没有获得稳定的
compression latency收益，默认仍遵从SOMA的AUTO产品策略。

Rejected candidate：不建立长期 raw-array/manual engine，不缓存 application authoritative frontier，
不通过降低统计频率或取消 deterministic order 取得数字。

## 5. SOMA Stage 3 裁决

Profile 证明剩余主要 SOMA-owned hotspot 是 Selection update/remove 的 candidate Chunk copy；point
PLAIN update 已经是 prevalidated in-place final commit，不能把二者混为一谈。

本专题不修改这一机制。继续优化需要正式设计 Selection mutation journal/delta staging，并重新证明：

- callback failure 之前不污染 authoritative payload；
- resource admission 覆盖 journal/candidate 的真实 peak；
- indexed Field、compressed/overlay Chunk、selection remove 与 packed mapping 一致；
- final commit bounded、non-throwing、一次 atomic publication；
- reference differential、fault injection、10K/1M/10M mutation 与其他 Example 防退化。

这是通用而有价值的未来性能专题，但不适合作为 Example 场景特供 patch。本次 Stage 3 因需要新的
正式机制设计而以`NO_PRODUCTION_CHANGE / FOLLOW_UP_CANDIDATE`关闭；没有弱化 correctness 或资源合同。

## 6. Qualification 与 claim boundary

通过的可重放入口：

```text
mvn -f soma-examples/simulation/pom.xml clean test
./scripts/check.sh
SOMA_BENCHMARK_SCENARIOS=simulation-application ... ./scripts/benchmark.sh
```

当前可以声称：

- SOMA 可以自然承载该 Grassing runtime-state model；
- UI/headless 使用同一 engine/model，headless 是 canonical benchmark path；
- fixed-host default/stress correctness、fingerprint 与上述 before/after 成立；
- Example 与 source delivery/package qualification 已闭合。

不能声称：

- 跨硬件绝对 SLA；
- UI FPS 是 SOMA kernel 性能；
- Selection mutation candidate-copy 已优化完成；
- GitHub Release/Package、签名或正式 publication 已授权。

本记录接管此前 Temporary 的稳定治理结论；Temporary 已完成 replacement closure 并退役。

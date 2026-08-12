# SOMA V1 Post-M2 性能优化治理

类型：Conformance / Performance Governance Closure

状态：`PASS / T1_T4_COMPLETED / TEMPORARY_CLOSED`

日期：2026-08-13

Owner：Physical Execution Engine M2之后T1–T4的设计边界、实现结论、性能证据、停止裁决与claim boundary

## 1. 结论

本专题在不改变Blueprint、public/generated API、Canonical IR语义、Reference Interpreter、scheduler、
资源Owner或atomic publication合同的前提下，依次完成了四个有界切片：

| Slice | 最终裁决 | Production结果 |
|---|---|---|
| T1 Relation Physical Execution | `PASS / IMPLEMENTED` | 删除Index lookup路径已经证明过的重复Join equality与静态compatibility检查 |
| T2 GroupBy Physical Execution | `PASS / IMPLEMENTED` | schema-known aggregate Field由optimized breaker直接typed读取，不再伪装成opaque callback |
| T3 Dense Selection Remove | `PASS / IMPLEMENTED / OWNER_PROMOTED` | replacement Key/Index由旧sidecar结构投影，不再逐row重建hash/equality/growth |
| T4 Construction / Index Build | `PASS / NO_CHANGE / DESIGN_BOUNDARY_IDENTIFIED` | 有限trusted-staging候选未达到收益门槛，production代码已撤回 |

专题没有引入第二套IR、执行器、Index truth、Loader/Batch、dependency、module、public hint或场景特供
路径。T1–T3保留的改动都删除了现有信息流中的重复工作；T4证明当前repeated atomic add的主要成本不
能通过删除少量generated carrier检查解决，更大的变化需要新的正式设计，因此本专题在边界处停止。

## 2. T1 Relation

### 2.1 根因与实现

单Field `RIGHT_INDEX_LOOKUP`在pipeline construction时已经验证两侧Field兼容，Index probe又用完整
typed equality选中了Bucket；production candidate loop却对Bucket中每个locator再次执行同一个
condition equality，并在hot loop重复静态compatibility检查。

当前实现把typed equality kernel明确为prevalidated internal入口，并把正确性证明保持在既有Owner：

- `GeneratedRelation.validateCondition`拥有schema compatibility；
- `IdentityHashIndex.firstJoin`拥有hash与complete typed-value Bucket选择；
- Bucket membership保证其locator共享同一Index logical value；
- Reference Interpreter仍执行完整equality，不复用production shortcut。

Hash/Cross/Outer语义、duplicate/null、canonical order、typed/callback filter、resource estimate与failure
均未改变。

### 2.2 Fixed-host证据

Amazon Corretto 8、Apple Silicon、相同schema/distribution与fresh-JVM测量；全部fingerprint `PASS`：

| Scale / Operation | Before | After | Change |
|---|---:|---:|---:|
| 1M Equality Join count | 250.913 ms | 108.926 ms | -56.6% |
| 1M typed filtered Join | 239.674 ms | 183.374 ms | -23.5% |
| 1M Semi | 134.187 ms | 94.262 ms | -29.8% |
| 1M Anti | 158.138 ms | 83.505 ms | -47.2% |
| 10M Equality Join count | 1.628 s | 0.954 s | -41.4% |
| 10M Semi | 1.328 s | 0.891 s | -32.9% |

10K Join count为`0.786 ms`，没有新增有意义的固定成本。10M explicit parallel filtered Join与sequential
接近，profile表明当前lookup仍主要受memory/decode约束；没有为了提高CPU占用率引入participant-local
Relation state或deterministic merge复杂度。

## 3. T2 GroupBy

### 3.1 根因与实现

公开API只允许schema-known `groupBy(field).sum(field)`，Canonical operation已经拥有aggregate Field与
numeric kind；旧optimized breaker却逐row经过generated lambda、View、query cursor与
`CallbackExecutionScope`读取aggregate value，把typed Field错误执行成opaque callback。

当前optimized path在binding后一次解析numeric leaf/slot/kind并直接读取bound directory。Reference
path继续使用generated mapper/View，因此两条读取链保持独立，numeric mapping、Infinity与overflow可做
差分。integer 128-bit accumulator、floating block/pairwise sum、group首次出现order、null group、result
shape、resource admission和failure合同均未变化。

### 3.2 Fixed-host证据

| Scale / Operation | Before | After | Change |
|---|---:|---:|---:|
| 1M low-cardinality long sum | 17.858 ms | 12.324 ms | -31.0% |
| 1M high-cardinality long sum | 60.042 ms | 44.886 ms | -25.2% |
| 10M low-cardinality long sum | 170.152 ms | 121.705 ms | -28.5% |
| 10M high-cardinality long sum | 520.252 ms | 440.572 ms | -15.3% |

10K low/high为`0.448 / 1.177 ms`，无固定成本退化。10M high-cardinality explicit parallel为
`437.510 ms`，与sequential基本等价；当前没有证据支持多份participant hash state、merge、order恢复
和更高resource peak，复杂并行GroupBy未准入。

## 4. T3 Dense Selection Remove

T3的稳定职责、correctness/failure/resource证明、AUTO/OFF相邻A/B与10M profile由独立
[Dense Selection remove sidecar projection治理](v1-dense-selection-remove-sidecar-projection-governance.md)
拥有。其正式结果是operation-scoped old-to-final locator projection和replacement sidecar structural
projection；旧payload-backed projected rebuild已经退出，相关稳定职责已写回Storage、Execution与Core
Design。

代表性结果：1M AUTO/OFF分别下降`53.3% / 56.2%`；10M AUTO从M2资格快照`2.679 s`下降到
`1.058 s`，约下降`60.5%`。这不是point remove复杂度声明，replacement sidecar仍需线性访问全部
membership并创建独立candidate。

## 5. T4 Construction / Index Build

### 5.1 Current事实

Current T3实现、Corretto 8、10M AUTO、`-Xms8g -Xmx24g`、24 GiB managed budget、1 warmup +
3 samples的repeated atomic add ingest median为`5,394.699 ms`，约185万rows/s，fingerprint `PASS`。

CPU profile主要归因于：

- `IdentityHashIndex.preflightAdd -> Shard.findProbe`；
- hash相同时读取representative payload完成typed equality，encoded Field进入RLE/dictionary decode；
- generated add的per-Field staging validation；
- Plain write、sidecar prepare/commit、managed accounting和偶发Chunk finish/encode。

### 5.2 有限候选与拒绝结论

唯一进入production spike的候选，是让full-regeneration processor生成的`add(detachedObject)`使用internal
trusted typed staging setter，删除每leaf重复的operation/thread/kind discovery；`row.add()`、duplicate、
resource、sidecar、payload和publication协议保持不变。

1M AUTO相同benchmark下，current正式基线约为`461.514 ms`，候选为`479.006 ms`，约退化`3.8%`。
它没有达到预先冻结的“至少改善5%”门槛。因此候选代码被完整撤回，不继续消耗10M/profile预算，T4以
`NO_CHANGE`关闭。

以下方向均没有被静默实现：

- Bucket typed-value snapshot：会改变Index retained memory与representation；
- reserve预建Index：secondary cardinality未知，且把成本移到计时外不等于端到端收益；
- publication batching、Loader或Batch：会改变每次add独立atomic operation边界；
- 跳过duplicate/equality或monotonic Key特供路径：会削弱正确性或通用性。

后续只有新真实场景与memory/performance证据证明收益足以覆盖产品复杂度时，才可建立新的bounded
Candidate；本记录不构成未来能力授权。

## 6. 资格与replacement closure

各实施切片均执行targeted unit/reference differential、受影响1M/10M benchmark、fingerprint与
`./scripts/check.sh`。T1、T2、T3分别由本地提交`07b0168`、`19e9823`、`8c0d6c8`形成可恢复检查点；
T4没有保留production delta。

最终统一资格结果：

- `./scripts/check.sh`：`PASS`，包括runtime `91 tests`、processor `34 tests`、Java 8 generated
  consumer与full regeneration；
- 三个reference application、benchmark qualification、local package、SBOM与provenance：`PASS`；
- `git diff --check`、Markdown relative link与Temporary route检查：`PASS`；
- 无新增dependency、module、public/generated surface、committed build artifact或release claim。

Post-M2路线Temporary中的稳定事实已由本记录、T3独立记录和正式Design接管；Candidate/roadmap不再是
current input并完成replacement closure。T5–T7只曾是未授权候选，未形成Design或current backlog；如
未来profile重新证明其价值，应从current HEAD重新建立bounded Temporary。

## 7. Claim boundary

本记录证明固定主机、冻结schema/distribution和当前实现上的相对防退化与收益，不构成跨硬件SLA、
一亿行资格或“世界最快”声明。它不授权GitHub push、Release、Package发布、签名或正式release声明。

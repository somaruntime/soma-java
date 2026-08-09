# SOMA V1 内存归因与低分配执行治理

类型：Conformance / Memory Attribution and Low-allocation Execution Governance Record

状态：`PASS`

日期：2026-08-09

Owner：retained、temporary reservation、Java allocation、heap/RSS 的归因边界，正常路径低分配优化，
固定 10M 资格与 claim boundary

## 1. 结论

本专题关闭了“把 peak RSS 与 SOMA retained 的差额全部解释成临时对象”的错误归因，并在不改变
Blueprint、正式 Design、public/generated API、dependency、production artifact、logical result/order/
failure 或 release boundary 的前提下完成正常路径低分配优化：

- benchmark-only type-kernel 现在按 operation 记录 participant-thread Java allocation、SOMA retained
  before/after、temporary/managed high-water、heap used/committed 与 GC delta；
- 10M Table 的最终 SOMA retained 仍为 `1,818,019,712` bytes（约 1,733.8 MiB），与优化前一致；
  storage representation、Key/Index 没有被偷换或漏计；
- 同配置 10M matched lane 的 peak RSS 从 10,835.8 MiB 降至 7,327.1 MiB（-32.4%），但该下降同时
  包含 benchmark oracle 去装箱与 runtime 低分配，不能全部归因给 SOMA runtime；
- 在相同 benchmark harness 下隔离旧 runtime 与候选 runtime 的 100K A/B，ingest participant allocation
  从 `121,501,952` 降至 `84,203,264` bytes（-30.7%），integer aggregate 从 `28,811,864`
  降至 `11,864` bytes，parallel floating 从 `22,423,064` 降至 `3,215,552` bytes；
- callback scope、Index prepared-add、zero-byte resource lease、Group operation token、exact-size
  materialization 与无有效 typed prefix 的 parallel source 都消除了可证明不必要的对象或数组；
- 逐条 add 的 atomic StateRoot publication、Key/Index consistency、resource preflight、canonical floating
  reduction 与 callback/one-shot 语义均保留；没有用 Loader/Batch、弱化原子性或降低资源上界换取数字；
- fixed 10M attribution 与 matched lane、runtime/processor、三个 reference application、default benchmark、
  local package 与 provenance 最终全部通过。

当前 G1-G10 继续为 `PASS`。本记录不是跨硬件 SLA、一亿行资格、GitHub Release/Package、Maven
publication、签名或正式 release 声明。

## 2. 四类内存事实

| 类别 | 含义 | 不能推导什么 |
|---|---|---|
| SOMA retained | 当前 StateRoot 可达的 physical representation、directory、Key/Index 与 engine-owned state | 不能代表整个 JVM heap 或 application referent graph |
| SOMA temporary reservation | terminal/mutation 为 candidate、scratch、task、result handoff 预先准入的保守上界 | 不能解释为同量对象已经分配或同时 live |
| participant allocated bytes | qualified JVM 上 caller 与 `ForkJoinPool-*` participant 的累计 Java allocation traffic | 不能解释为 live set、retained set 或 peak RSS |
| heap/RSS | heap used/committed、GC 与 process resident pages 的独立观测 | `RSS - retained` 不能直接命名为 SOMA temporary object |

额外边界：

- `heap used` 没有在每个 operation 前后强制 full GC，因此是瞬时 usage，不是 post-GC live heap；
- `heap committed` 是 JVM 可用容量，不等于已经使用；
- RSS 还包含 native JVM、code cache、JIT/JFR、thread stack、class metadata 与 page residency；
- ordinary Object referent、application callback 分配、expression/literal snapshot 和已经返回给 application
  的 detached result 不属于 Table retained representation；
- `AllocatedBytes` 若 JVM 不支持 thread-allocation MXBean 则输出 `-1`；当前 qualified Corretto 8 支持。

这四类事实并列记录，任何一类都不替代另一类。

## 3. Evidence boundary

| 项目 | Controlled A/B | Fixed 10M attribution / matched lane |
|---|---|---|
| Runtime baseline | `develop@d928955108ed20387510225a4220288c7bceb051` | 同 baseline 的治理前 10M raw evidence |
| Candidate | 本记录所在 commit | 本记录所在 commit |
| Host | Apple M5 Pro，48 GiB physical memory | 同左 |
| OS | macOS / Darwin arm64 | 同左 |
| Java / Maven | Amazon Corretto `1.8.0_502` / Maven 3.9.16 | 同左 |
| Workload | type-kernel，100,000 rows，AUTO，P8 | type-kernel，10,000,000 rows，AUTO，P16 |
| JVM / SOMA budget | `-Xms2g -Xmx8g` / 6 GiB | `-Xms8g -Xmx24g` / 16 GiB |
| Sampling | one fresh JVM；无 warmup + one sample；同一 attribution harness | one attribution JVM + one profiler-matched JVM；无 warmup + one sample |
| Correctness | operation assertion + shared fingerprint | 同左 |

100K 不是新的规模资格线，只用于低成本、同 harness 的 runtime source A/B。10M 是唯一大型数据规模；
attribution lane 用 JFR 与 operation replay 采集分层指标，matched lane 使用与治理前相同的 async-allocation
profiler、不执行 replay，避免把观测开销写成 runtime 性能变化。Raw JSONL、environment、rusage、GC/JFR
与 flame graph 只保留在本机临时 evidence；长期可重放入口是
[`scripts/benchmark.sh`](../../scripts/benchmark.sh)。单次同机数字只用于本专题归因，不提升为 SLA。

## 4. Operation attribution

### 4.1 Controlled runtime A/B

以下为相同 100K harness、JVM、workload、P8 与 memory-attribution contract 的 participant allocation：

| Operation | Baseline bytes | Candidate bytes | Delta |
|---|---:|---:|---:|
| ingest | 121,501,952 | 84,203,264 | -30.7% |
| integral aggregate family | 28,811,864 | 11,864 | -100.0% |
| floating aggregate family | 16,010,688 | 3,210,816 | -79.9% |
| parallel floating family | 22,423,064 | 3,215,552 | -85.7% |
| reference stateful family | 6,341,688 | 6,326,624 | -0.2% |
| detached object materialization | 4,008,848 | 404,392 | -89.9% |
| Value GroupBy | 2,539,656 | 1,735,216 | -31.7% |
| sparse / medium / dense GroupBy | 142,768 / 3,748,312 / 5,316,312 | 74,488 / 1,881,696 / 1,881,696 | -47.8% / -49.8% / -64.6% |
| floating GroupBy | 6,023,816 | 2,817,296 | -53.2% |
| Value Equality Join scalar result | 6,008,392 | 3,952 | -99.9% |

两侧最终 retained 都是 `36,523,120` bytes。allocation 下降不是通过少存 Field/Index 得到的。

### 4.2 Fixed 10M attribution

候选 10M attribution lane 的分层事实：

| Operation | Participant allocated | Peak temporary reservation | 说明 |
|---|---:|---:|---|
| ingest | 7,243,365,856 B | 1,733,627,512 B | 累计分配包含原子 StateRoot/guard、Index/codec growth；reservation 是 chunk publish 时的保守 candidate 上界 |
| integral aggregates | 11,864 B | 0 B | primitive hot path 已接近 allocation-free |
| floating seq / explicit parallel | 320,010,816 / 320,015,552 B | 80,000,032 / 80,000,032 B | canonical 1024-block reduction 需要 deterministic value sequence；parallel 无无效 membership copy |
| reference stateful | 763,987,728 B | 960,018,784 B | distinct/order state 与 reference result 是真实工作 |
| detached Table object array | 40,004,392 B | 2,880,000,032 B | exact staging 直接返回；reservation 仍按宽 detached row 保守准入 |
| Value GroupBy | 17,829,936 B | 1,440,000,032 B | retained 不增长；lease 继续覆盖 worst-case result/scratch |
| sparse / medium / dense GroupBy | 1,881,696 B each | 1,440,000,032 B each | actual allocation 按真实 group growth，不等于 lease upper bound |
| floating GroupBy | 325,717,144 B | 1,760,000,032 B | canonical per-group floating sequence 保留 |
| Value Equality Join scalar result | 3,952 B | 12,582,944 B | pair visit + scalar accumulator 不物化完整 Join output |

10M ingest 前 `reserve()` 和固定 lookup Table 已建立，因此 retained before 为 `1,036,104,704` B；ingest
后最终 retained 为 `1,818,019,712` B。采样到的 ingest peak managed 为 `3,749,931,560` B、peak heap
used 为 `5,687,299,464` B、peak heap committed 为 `10,380,378,112` B；ingest 期间 3 次 GC、累计
245 ms。它们分别是 accounting、瞬时 heap 与 JVM capacity，不合并成一个“Table 大小”。

## 5. Low-allocation implementation

### 5.1 Benchmark isolation

type-kernel ingest 复用一个 application `KernelRecord` 输入对象，并将 correctness oracle 从逐行
`LinkedHashMap<String, Long/Double>` 改为固定 schema 的 ordered primitive arrays。oracle 仍保持首次
encounter order、exact value 与 fingerprint，但不再让 benchmark 自身的 boxing/entry graph 污染 SOMA
归因。这是 matched 10M peak RSS 大幅下降的主要 Owner 之一，不冒充 runtime storage 优化。

### 5.2 Callback scope

callback marker 从每行 `ThreadLocal<Integer>` boxing/set/remove 改为每个 participant thread 复用一个
mutable depth。caller 在 Group operation 结束时清理；长期 ForkJoin worker 最多保留一个小 marker，
是 O(P) 而非 O(rows) allocation。

### 5.3 Atomic add scratch

Key 与全部 Index 的 `PreparedAdd` 由 Table 预分配复用。每次 prepare 只有在验证和计算完整后才填充，
commit 后或任意 failure/retry 都在 `finally` 清除 owner/reference；新增 duplicate-key 与 injected-failure
后继续 add 的回归，证明 scratch 没有 stale state、引用滞留或 partial publication。

### 5.4 Resource and operation carriers

- zero-byte retained/temporary request 使用 immutable shared no-op lease；正数 accounting 路径不变；
- Group guard 将原 operation token + lease 两对象合并为每次 operation 唯一 lease；CAS ownership、
  provenance identity、reentrant/concurrent failure 与 close 仍由同一对象拥有；
- ordinary add 仍发布新的 immutable `TableStateRoot`。这是原子 currentness 的正式成本，本专题没有为
  减少对象而改为 mutable root。

### 5.5 Query materialization and parallel source

- 当 detached reference array 的实际 cardinality 等于已准入 upper bound 时，直接返回 typed staging，
  不再复制第二个等长 array；
- explicit `parallel()` 的 normalized Table scan 如果没有可并行 typed-filter prefix，不再创建 identity
  locator/membership buffer。SOMA 的合同是最多 P participants，并不保证每个 terminal 都创建 task；
  sequential result、encounter order 与 fingerprint 不变；
- 有 typed prefix 的原 bounded scheduler、participant-local work、ordered merge 和 reservation 继续使用。

## 6. Matched 10M before / after

相同 async allocation profiler、JVM、10M、AUTO、P16、无 attribution replay 的单次 matched evidence：

| Metric | Baseline | Candidate | Delta |
|---|---:|---:|---:|
| ingest | 6.650 s | 5.640 s | -15.2% |
| integral aggregate family | 3.134 s | 2.604 s | -16.9% |
| floating sequential / parallel | 1.732 / 1.867 s | 1.540 / 1.556 s | -11.1% / -16.7% |
| reference stateful | 528.330 ms | 488.577 ms | -7.5% |
| detached object materialization | 415.137 ms | 362.802 ms | -12.6% |
| medium / dense GroupBy | 596.788 / 1,021.093 ms | 536.813 / 905.818 ms | -10.1% / -11.3% |
| floating GroupBy | 979.671 ms | 844.794 ms | -13.8% |
| Value Equality Join | 4.337 s | 3.855 s | -11.1% |
| process wall / CPU | 38.58 / 45.09 s | 33.71 / 38.26 s | -12.6% / -15.1% |
| peak RSS | 10,835.8 MiB | 7,327.1 MiB | -32.4% |
| final SOMA retained | 1,818,019,712 B | 1,818,019,712 B | unchanged |

两侧 correctness/shared fingerprint 一致，major page fault 都为 0。单次绝对 latency 不是稳定性能 claim；
本表用于确认低分配改动没有把 memory traffic 转化为明显 CPU/wall regression。

## 7. Correctness and qualification

保留的不变量包括：

- per-add all-or-nothing、Key/Index/payload/managed accounting 同一 StateRoot generation；
- duplicate Key、failure injection、retry 后 scratch clean 与 zero partial publication；
- reference、optimized sequential、explicit parallel 的 logical result/fingerprint 与 canonical order；
- signed-128 integer、canonical floating tree、callback scope、one-shot lifecycle 与 structured failure；
- materialization/resource preflight、lease release、Group guard 与 detached result ownership；
- Java 8、exactly runtime/processor 两项 production artifact、无新 dependency。

最终 evidence：

- runtime：60 tests，0 failure/error；
- processor：34 tests，0 failure/error；
- fixed 10M attribution 与 matched lane：2 records，correctness/fingerprint 均 `PASS`；
- 三个 reference application、compression/metadata、source delivery、local package、SBOM/checksum/
  provenance：`PASS`；
- default core benchmark、`scripts/check.sh`、`scripts/qualify.sh`、`git diff --check` 与 Markdown route：
  `PASS`。

## 8. Current boundary

1. `AllocatedBytes` 是累计 traffic。10M ingest 的 7.24 GB 不表示同时 live 7.24 GB；其 peak heap used
   为 5.69 GB，最终 Table/Index retained 为 1.82 GB。
2. per-add unique Group lease 与 immutable StateRoot 是当前原子 publication/provenance 的必要成本；若未来
   要消除它们，必须先重新设计 mutation transaction，而不是在 hot path 偷改语义。
3. floating sum/average 与 floating GroupBy 的数组服务于正式 canonical numeric tree；除非新的算法能
   证明 bitwise-equivalent sequential/parallel result，否则不以较低 allocation 替代它。
4. materialization/GroupBy 的 temporary reservation 明显高于 actual allocation，是 conservative admission，
   不是内存泄漏。未来可在不降低 fail-closed 上界的前提下继续收窄 shape/cardinality proof。
5. `parallel()`没有有效 typed prefix 时由 optimizer 选择顺序 physical source；真正 parallel Relation/Group
   仍需 participant-local architecture，继续服从已有四维治理边界。
6. benchmark memory attribution 当前只接入 type-kernel；三个 reference application 保持长期默认
   performance/correctness lane，不承担诊断字段噪声。
7. 一亿行仍是架构愿景；本记录证明的是当前机器上固定 10M normal-path memory attribution 与低分配资格。

## 9. Replacement closure

| Temporary responsibility | Stable Owner | Disposition |
|---|---|---|
| memory category/measurement contract | benchmark source、README 与本记录 | PROMOTED |
| operation allocation and managed/heap snapshots | benchmark source + stable script | PROMOTED |
| low-allocation runtime implementation | runtime source + targeted tests | PROMOTED |
| controlled A/B and fixed 10M evidence | 本记录 | PROMOTED |
| `RSS - retained = temporary object` | 不进入正式事实 | REJECTED AS INVALID INFERENCE |
| Loader/Batch、mutable StateRoot、automatic parallel | 不进入本专题 | REJECTED BY BOUNDARY |
| bounded Temporary | 删除 | RETIRED |

本记录关闭本专题；当前没有 active bounded topic。

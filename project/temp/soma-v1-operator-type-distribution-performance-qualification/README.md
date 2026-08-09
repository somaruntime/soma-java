# SOMA V1 Operator × Type × Distribution 性能资格与第二阶段架构优化

类型：Temporary / bounded performance governance topic

状态：`ACTIVE`

日期：2026-08-09

Owner：正常使用路径下的执行内核覆盖、数据分布敏感性、profile 归因、第二阶段内部优化与 replacement closure

## 1. 治理目标

上一轮四维治理已经证明三个 reference application 的固定千万行组合负载可运行，并关闭了
GroupBy actual-cardinality 与 Relation scalar aggregate 的已知热点。本专题不增加数据规模档位，
而是补齐 Examples 没有覆盖、但正式 V1 type system 已经承诺的物理内核，再用 profile 判断是否存在
第二阶段优化空间。

本专题只回答：

1. 不同 logical type 是否落入预期的少数 physical kernel；
2. 低/高基数、null、skew、selectivity 与 Join multiplicity 是否暴露明显的时间或内存异常；
3. reference application 与 synthetic kernel 两类正常路径的 fingerprint 是否稳定；
4. 哪些热点能在不改变 Blueprint、Design、public/generated API 与 failure/resource 语义的前提下优化。

## 2. 固定边界

- 不新增 production module、artifact、dependency 或公开 API；
- 不修改 Blueprint 与正式 Design；
- 不把 synthetic kernel 伪装成第四个 Example；它只属于 `benchmarks/` 的 non-production evidence；
- 不增加新的 row-count 档位：breadth lane 固定 1M，只有确认昂贵或异常的代表内核进入固定 10M；
- 不穷举 Type × Operator × Source 的笛卡尔积；同一 physical kernel 只保留有不同 lowering、
  memory layout、equality/order 或 execution path 的代表；
- 只验证正常使用路径；非法调用只在会破坏架构、资源或正确性边界时由既有 qualification 负责；
- 不以低 CPU 使用率为由包装共享 mutable cursor；parallel 优化必须保持 participant-local state、
  canonical order、first failure、resource lease 与 reference differential。

## 3. 最小充分覆盖矩阵

| Kernel family | Type representative | Operator / source | Distribution |
|---|---|---|---|
| narrow integral | byte / short / char / int / long | Field projection、sum、typed filter | 周期分布 |
| floating reduction | float / double | sum、average、GroupBy sum、sequential/parallel | 精确可复算 finite values |
| intrinsic reference | String / Enum | Index、isNull、distinct/order、GroupBy | null + low cardinality + skew |
| structural value | nested `@SomaValue` | Index、GroupBy、Equality Join | high cardinality + duplicate right matches |
| opaque reference | ordinary Object | storage、projection、materialization | pooled identity + null |
| filtered grouping | indexed/non-indexed reference key | 1% / 50% / 99% typed filter 后 GroupBy | sparse / medium / dense |

三个 reference application 的 composed workload 继续拥有真实场景 coverage；本表只填补它们缺失的
type/distribution kernel，不复制业务流程。

## 4. Evidence lanes

### 4.1 Breadth lane

- 1,000,000 rows；
- 1 fresh JVM 可用于诊断，正式 retained evidence 使用 3 fresh JVM；
- 每个 metric 自带 deterministic expected value 或 fingerprint；
- 收集 wall、CPU、RSS、GC 与必要的 JFR/async profile。

### 4.2 Hotspot lane

- 固定 10,000,000 rows；
- 只重放 breadth 中耗时显著、内存放大或 scaling 异常的 6–10 个代表内核；
- JVM 与 SOMA budget 沿用上一轮 `-Xms8g -Xmx24g` / 16 GiB；
- before/after 必须在相同机器、JDK、配置与 fingerprint 下比较。

## 5. Stop rules

出现以下任一情况即停止相应实现并保留证据：

- 需要改变 logical result/order/null/equality、public/generated surface 或 Design semantic；
- 需要第三 production artifact、新 dependency 或 release 权限；
- 优化让正常场景超过既有 15% / 2 ms regression ratchet；
- profile 不能把收益归因到明确 Owner；
- candidate 只改善 synthetic workload，却使三个 reference application 的 retained hotspot 退化；
- 需要通过重复审查、扩大异常输入集合或新增规模档位才能“制造”收益。

## 6. Exit criteria

1. benchmark-only schema、runner 与 script route 在 Java 8 full-regeneration 下可重放；
2. 五类 kernel 与三类 distribution 都有正常路径 correctness fingerprint；
3. 1M breadth 与必要的 10M hotspot evidence 完成；
4. retained optimization 有 before/after、targeted correctness 与 full qualification；
5. rejected candidate 不留下 production dead code；
6. 稳定事实晋升到单一 Conformance Owner，更新入口，删除本 Temporary；
7. `scripts/check.sh`、`scripts/qualify.sh`、Markdown route 与 `git diff --check` 通过。


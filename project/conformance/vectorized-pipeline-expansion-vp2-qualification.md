# SOMA Vectorized Physical Pipeline扩展 VP2 资格

类型：Conformance / Implementation Slice Qualification

状态：`PASS / VP2_COMPLETED / VP3_READY / RELEASE_NOT_AUTHORIZED`

Owner：ordered `long[]` representation-native materialization、bounded parallel partition与对应资源、正确性、性能证据

最后更新：2026-08-12

## 1. 结论

VP2已在VP1 single final Physical decision与representation handler基础上完成。Schema-known `long` Field
materialization现在具有一个正式内部执行族：

- PLAIN直接读取`long[]`；
- encoded plain直接读取；
- single-required-leaf RLE按run直接填充；
- multi-leaf RLE和overlay/current-value保持scalar fallback；
- 无predicate sequential/parallel按Chunk canonical ordinal写入固定不重叠区间；
- pure typed predicate的explicit parallel采用count → checked prefix → disjoint write；
- 所有返回值都是detached、exact-length、canonical-order `long[]`。

Public/generated API、Canonical/Reference语义、Java 8、两项production artifact、dependency与release边界未改变。
其他primitive array、reference materialization、callback/mapped/stateful、sub-Chunk与新scheduler均未准入。

## 2. Evidence-driven kernel裁决

冻结计划以two-pass作为typed predicate materialization候选机制。Matched profile证明：

- explicit parallel two-pass显著消除旧O(N) locator/staging路径；
- sequential two-pass会重复扫描同一predicate，成本高于一次写入加一次连续array copy。

因此最终L4机制在同一final Physical decision内按execution mode收口：

```text
sequential typed predicate
    -> admit upper-bound staging + possible exact result
        -> one representation-native write pass
            -> exact-length compact copy only when partial

parallel typed predicate
    -> admit upper-bound result + O(C) counts/offsets + bounded task state
        -> per-Chunk count
            -> checked canonical prefix
                -> exact result
                    -> disjoint per-Chunk write
```

该裁决没有引入第二planner、scheduler或semantic executor；两条路径消费同一KernelPlan与representation
handler。它是由性能门槛触发的private mechanism收口，不改变用户合同。关闭的“sequential也强制two-pass”
实现已删除，没有feature flag或dead branch。

## 3. 正确性、资源与失败证据

最终Java 8 runtime breadth：

```text
mvn -q -pl soma-runtime test
86 tests, 0 failures, 0 errors
```

新增证据覆盖：

- empty、all-match、no-match、partial-match；
- multi-Chunk、logical RLE run跨Chunk boundary、mixed encoded/plain root与overlay；
- Reference vs sequential vs parallel exact array、canonical order与detached result；
- P=1/2/4/16；
- unavailable pool的stable structured failure；既有scheduler tests继续拥有rejection/interrupt/quiescence；
- parallel predicate final ResourceEstimate精确包含`N × 8` upper-bound、两个O(C) int数组和bounded task state；
- final plan删除旧O(N) parallel locator prefix；tiny-budget在pool submission、count pass和data work前拒绝；
- operation结束后temporary accounting归零；parallel路径没有O(N) companion staging buffer。

不带predicate的parallel路径只拥有detached result与bounded task state。Sequential partial路径保守准入
`N × 16`，覆盖upper-bound staging与exact result的实际共存峰值；all-match直接返回staging，不分配第二数组。
所有size、prefix与resource arithmetic保持checked structural/long domain。

Production/public generated surface未变化，无新增consumer ABI需要冻结。按Product Owner“不使用subagent”的
明确要求，本slice由主Agent在targeted、runtime breadth和matched profile之后执行一次bounded code/resource
review，没有用重复审查替代交付。

## 4. 同机性能资格

固定环境：Amazon Corretto `1.8.0_502`、Maven `3.9.16`、Apple M5 Pro、ParallelGC、1M rows、
P=16、`-Xms2g -Xmx8g`、SOMA budget 6 GiB。Baseline为VP1提交`85e37ba`；为baseline和candidate
使用同一新增benchmark观察项，均为3个fresh JVM、每个JVM 1次warmup和3个sample。Cross-run median：

| Cell | VP1 baseline | VP2 candidate | 变化 | 判断 |
|---|---:|---:|---:|---|
| AUTO Field `long[]` sequential | 0.964 ms | 0.366 ms | -62.0%，约2.63x | PASS |
| AUTO Field `long[]` parallel | 5.515 ms | 0.200 ms | -96.4%，约27.6x | PASS |
| AUTO typed materialization sequential | 11.140 ms | 6.739 ms | -39.5%，约1.65x | PASS |
| AUTO typed materialization parallel | 13.533 ms | 4.450 ms | -67.1%，约3.04x | PASS |
| OFF Field `long[]` sequential | 0.332 ms | 0.321 ms | -3.3% | no regression |
| OFF Field `long[]` parallel | 5.493 ms | 0.167 ms | -97.0%，约32.9x | PASS |
| OFF typed materialization sequential | 6.332 ms | 6.385 ms | +0.8% | within noise |
| OFF typed materialization parallel | 13.368 ms | 4.191 ms | -68.7%，约3.19x | PASS |

所有admitted parallel/AUTO cell越过冻结噪声门槛，PLAIN/OFF sequential没有稳定退化。Callback、mapped、
stateful等fallback code未改变，并由同一frontier source run继续保持correctness/fingerprint `PASS`。本表只
是固定主机slice regression，不是跨硬件SLA。

## 5. Slice边界与下一步

VP2已关闭，允许进入VP3。VP3不新增能力，只负责10K/1M/10M与reference application回归、全仓
qualification、正式Design Owner晋升、Conformance总记录、入口同步和本Temporary replacement closure。

GitHub Release/Package、签名、Maven publication和正式release声明仍未授权。

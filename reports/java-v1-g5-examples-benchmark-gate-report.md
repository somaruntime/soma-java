# Java-only SOMA V1 G5 examples/benchmark gate report

状态：passed
日期：2026-07-11
Gate：G5 examples/benchmark gate
Owner：root
执行人：Codex
Capability：`V1-SCENARIO-BENCHMARK` → `evidenced`
Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`（G6 未通过，当前 blocked）

> 当前性说明（2026-07-20）：本报告是 2026-07-11 的 v2 G5 Gate 快照；其中 maintained order、dirty selector 和旧 benchmark schema 已由 `4b6fa43` 的 packed/exact v3 切换取代。当前场景、benchmark 与重验证入口见 [2026-07-17 专题收口报告](2026-07-17-packed-exact-index-runtime-redesign-report.md)。本文保留当时 Gate 结论，不定义当前 runtime 形态。

## 1. 结论

G5 required evidence 已通过：四个 Java 8 formal scenarios、Access Pattern Cards、FJSP canonical frontier E2E、错误/生命周期/stats、generated schema/API/hash、20 条真实 integrated benchmark workload、结构化 JSONL、strict persisted-artifact validator、allocation/bytecode shape 与双 JDK 8 完整回归均有可核验证据。

该结论满足“完整 V1 功能范围 + G0–G5”的功能 RC 边界，但不构成 public RC artifact、G6、release readiness、性能优势或跨平台支持声明。

## 2. Contributor evidence

- examples：`soma-examples/reports/java-v1-g5-examples-report.md`；
- benchmark：`soma-benchmarks/reports/java-v1-benchmark-smoke-report.md`；
- 既有 runtime/shape/consumer evidence：G2、G3、G4 reports；
- scope、RC 与 claim boundary：G0 report、`docs/validation-gates.md`、`docs/versioning-and-release-contract.md`。

四个场景覆盖 FJSP、VRP、Simulation、Game。FJSP 的两个 operation 都执行 release → grouped update → dynamic sort → assignment/machine mutation → grouped remove；第二个 operation 还执行正值 setup-time lookup。其他场景覆盖各自正式 Owner 要求的 table/access breadth。

Benchmark exact manifest 为 G5 §9 的 20 条 minimum integrated workload。每条 record 绑定固定 `workloadId` 和 lane-specific `workloadEvidence`；primary/repeat artifact 都由独立 validator 重新解析，8 条 negative artifact case fail closed。所有 record 固定 `level=smoke`、`claimAllowed=false`。

## 3. Validation record

验证对象：Phase 6 implementation commit `2597c81895ea024867b2c8bb2eafab175e6cea5c`。输入artifact为四个formal schema、正式Owner和20-lane manifest；输出artifact为generated examples JAR/classes、scenario transcript与benchmark JSONL/schema/checksum。

命令：

```text
./scripts/check.sh
env JAVA_HOME=/tmp/corretto8-soma/Contents/Home PATH=/tmp/corretto8-soma/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin ./scripts/check.sh
```

结果：

- Azul Zulu OpenJDK `1.8.0_492-b09`：`project-check: ok`；examples `target/phase6-examples.dNtVgO`；benchmark `target/benchmark-smoke.mTZgj5`；
- Amazon Corretto `1.8.0_492-b09` / `8.492.09.2`：`project-check: ok`；examples `target/phase6-examples.BxKJHi`；benchmark `target/benchmark-smoke.4Cn9fu`；
- post-fix exact benchmark：Zulu `target/benchmark-smoke.l5NVUF`、Corretto `target/benchmark-smoke.YL4tuu`，各20 primary + 20 repeat、12 negative；
- 最终Zulu全量总检：`project-check: ok`；examples `target/phase6-examples.Wt58xz`；benchmark `target/benchmark-smoke.LY9fqL`；
- Maven Wrapper / Apache Maven `3.9.16`；macOS `26.5.2` / Darwin `25.5.0`；arm64/aarch64；
- 失败 0；跳过 0；豁免 0。

examples 生成 200 个 exact-manifest type、共 711 个 class，全部 classfile major 52。Benchmark primary/repeat 各 20 条，8 个 malformed artifact negative path 全部被拒绝。

## 4. V1 scope non-regression

| 审计项 | 之前 | 本次之后 |
|---|---|---|
| `V1-SCENARIO-BENCHMARK` | `in-progress` | `evidenced` |
| G5 | `not-started` / contributor pending | `passed` |
| G6 | 未关闭 | 不变，仍由 release readiness report 拥有 |
| 唯一 V1 Goal | active | blocked（G6外部发布事实不足） |

- Owner、正式契约、Ledger、Gate 和 release claim 未变化。
- 尚未满足的发布 breadth 全部保留在原 Phase 6 / G6，不被 G5 隐式删除。
- 后续是 additive release evidence completion 或 contract-preserving internal refinement。
- 不存在 temporary contract、temporary hot path、public/generated migration 或 canonical-path rewrite。

## 5. Known limitations

- benchmark smoke 是 correctness/shape evidence，不是 claim-grade 性能测量；
- 本机双 vendor 结果不能外推为跨 OS/architecture 或完整 vendor/update 支持矩阵；
- 当前工作树尚未形成 clean immutable public candidate，因此该 G5 closeout不能替代 G6 provenance；
- G6 未通过，禁止公开发布、tag、publish 或声明 release ready。

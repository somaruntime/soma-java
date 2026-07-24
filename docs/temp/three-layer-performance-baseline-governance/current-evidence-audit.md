# Stage 0 当前性能证据审计

类型：Temporary

状态：active

Owner：SOMA three-layer performance baseline current evidence audit

事实范围：治理起点的 benchmark runner、artifact、validator、Gate、环境、当前数值和缺口

非事实范围：正式性能阈值、跨环境结论、public claim、SOMA 产品语义和未来实现决定

最后审查日期：2026-07-24

## 1. Immutable 起点

治理前代码与正式文档起点为：

```text
05e7f93 docs: synchronize simulation governance provenance
```

起点工作树干净；`docs/temp/` 只有 `.gitkeep`。当前正式性能 Report 明确所有
artifact 为 `claimAllowed=false`，G6 与跨环境 claim 不在本专题范围。

Stage 0 已于 2026-07-24 在 Azul Zulu full JDK 8
`1.8.0_492-b09`、macOS `26.5.2`、aarch64 上重跑 `./scripts/check.sh`，最终输出
`project-check: ok`。本协议、审计和该验证结果由 Stage 0 commit 一起形成新的
immutable starting point。

## 2. 当前三条 evidence lane

| 层 | 当前 Owner / 入口 | 已有强项 | 尚缺 |
|---|---|---|---|
| Component | `soma-benchmarks` / `check-post-cutover-components.sh` | 16 allocation + 24 exact-index memory records；strict validator；checksum；少量 allocation envelope | 单 JVM；无版本化 baseline；无统一环境适用性和比较结果 |
| Industrial scheduler integrated | child test/evidence / `check-industrial-scheduler.sh` | 3 独立 JVM fork × 3 measurements；稳定 input/result/schema/plan identity；allocation、GC、high-water | artifact 无完整环境/commit；只有正值检查；无 baseline/comparator |
| Grassing simulation integrated | child test/evidence / `check-grassing-simulation.sh` | 3 独立 JVM fork × 3 measurements；稳定 config/scenario/result/schema/plan identity；allocation、GC、high-water | artifact 无完整环境/commit；只有粗 allocation cap；无 baseline/comparator |

`check-benchmark-smoke.sh` 证明 lane、artifact 和基本 invariant 可执行，不是性能
回归基线。`check-scan-code-size.sh` 已拥有 generated footprint candidate + 15%
ceiling，也不等同于运行时性能 baseline。

## 3. 当前本机证据

当前正式 Report 的测量环境为 Azul Zulu OpenJDK `1.8.0_492-b09`、macOS
`26.5.2`、arm64/aarch64。

### 3.1 Component

- packed zero-stage count：`4.4272 B/op`；
- exact zero-stage count：`88.0928 B/op`；
- exact one-filter count：`192.0736 B/op`；
- exact three-stage count：`240.0736 B/op`；
- exact five-stage overflow count：`464.0736 B/op`；
- exact filter-sort scalar Index：`272.0736 B/op`；
- long ColumnTraversal：`44.5968 B/op`；
- exact-index primitive payload retained slack：`0..63 bytes`。

这些数字只描述当前 artifact，并不自动成为本专题最终阈值。

### 3.2 Industrial scheduler

Default workload 为 192 operations，1 warmup、3 independent forks、每 fork 3
measurements：

- `solveNanos` 总计范围 `30,269,375..30,632,291`；
- allocated bytes `8,089,768..8,089,848`，约 `14,045 B/operation`；
- Young/Full GC count 和 pause 都为 `0`；
- exact/update/operation scratch high-water 为 `3,015 / 7,560 / 368 bytes`。

### 3.3 Grassing simulation

Default workload 为 800 initial individuals、500 ticks，maximum population 1,139，
1 warmup、3 independent forks、每 fork 3 measurements：

- `tickNanos` 总计平均 `67,169,264`；
- allocated bytes `7,439,536`，约 `4,960 B/tick`；
- Young/Full GC count 和 pause都为 `0`；
- exact/update/operation scratch high-water 为
  `64,333 / 27,336 / 12,776 bytes`；
- population table growth count 为 `1`。

## 4. 缺口分类

### P1：没有版本化、环境感知的 baseline contract

当前 JSONL 是 measurement artifact，不是 checked-in baseline。runner 输出的
workload 和 identity 足以做 correctness guard，但 component 与 application
artifact 的环境字段不一致，两个应用缺少 commit 和完整 JVM/OS identity。

若直接对数字加阈值，会把当前电脑的结果伪装成普适 Gate；若完全不比较，则多 fork
只能发现明显异常，不能持续识别回归。

### P1：指标没有按稳定性和语义分类

Allocation、deterministic high-water、GC、wall-clock 的噪声与含义不同。当前脚本
主要检查正值或单个粗 cap，既不能表达“必须完全一致”的结构指标，也不能表达
“允许有限波动”的 timing 指标。

### P1：没有 baseline 更新纪律和负路径

当前没有区分测量、比较和显式 rebaseline；普通 Gate 若未来负责写 baseline，会导致
回归自我批准。也没有系统证明 schema/version、workload、identity、environment、
metric 缺失或不匹配时会被拒绝或标记为不适用。

### P2：三层责任尚未正式化

Design/Engineering 已区分 neutral component、integrated A/B 和 public claim，
Implementation Map 也区分 benchmark 与应用 Owner，但尚未形成统一的三层名称、
artifact promotion 规则和聚合结论。`claimAllowed=false` 目前主要是字段与文字约束，
还缺少“本地 baseline 不能晋升 public claim”的可执行保护。

## 5. Stage 0 裁决边界

Stage 0 只确认问题和治理协议，不决定最终阈值或实现结构。Stage 1 必须先回答：

- baseline 是 measurement 的何种聚合投影，哪些字段属于 identity；
- 环境兼容如何判定，`NOT_APPLICABLE` 是否仍允许完整 Gate 通过；
- exact、bounded、monotonic 和 noisy metrics 分别怎样比较；
- timing 用 median、percentile、dispersion 还是组合规则；
- component 单 JVM 是否升级为多 fork，成本如何控制；
- application 之间共享的是 evidence protocol 还是 Java implementation；
- baseline 生成、review、更新和防静默重写如何分责；
- 综合 Gate 怎样汇总三条 lane，又不产生 public claim。

## 6. Stage 0 判定

值得治理，且可以在不改变产品/领域语义的边界内完成。收益是把现有成熟 evidence
变成可持续回归保护；主要风险是假精确阈值、环境误判和 evidence 代码反向耦合两个
独立应用。上述风险必须由 Stage 1 详细设计和多 fork 校准解决。

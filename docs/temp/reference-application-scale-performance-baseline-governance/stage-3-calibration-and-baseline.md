# Stage 3 校准与 Baseline

类型：Temporary

状态：completed（Stage 3）

Owner：SOMA reference application scale performance baseline governance

正式事实源：否

事实范围：immutable candidate 的 9-fork 校准、threshold 和独立普通 Gate

非事实范围：public performance claim、支持矩阵和 release readiness

候选提交：`1af43ac`

环境：Zulu JDK `1.8.0_492-b09`、macOS `26.5.2`、aarch64、Apple M5 Pro

最后审查日期：2026-07-24

## 1. 校准完整性

六个 profile 均使用 9 个独立 JVM fork。每组 artifact 的 commit、环境、
input/result checksum、Schema hash、RuntimePlan hash、workload identity、
capacity/population 和三个 high-water 在 fork 间满足既定稳定性规则。

校准公式：

```text
allocation = ceil(max × 1.05)
timing     = ceil(max(p50 × 1.50, p90 × 1.25))
p90        = nearest-rank
GC count   = 0 if max=0, otherwise max+1
GC pause   = 0 if max=0, otherwise ceil(max × 1.25)
deterministic high-water = all-equal
```

GC 使用离散 count 与 pause 的明确余量，不把稳定存在的 collection 改写为零。
所有 baseline 继续 `claimAllowed=false`。

## 2. 9-fork 结果与 threshold

| Application/profile | Hot operation range / median | Timing limit | Allocation range | Allocation limit |
|---|---:|---:|---:|---:|
| scheduler default | `30.387..32.105 / 31.159 ms` | `46.738 ms` | `16.070..16.075 MB` | `16.878 MB` |
| scheduler large | `8.552..8.874 / 8.694 s` | `13.041 s` | `411.337..414.294 MB` | `435.008 MB` |
| scheduler long-run | `130.108..138.278 / 133.318 ms` | `199.976 ms` | `39.731..42.415 MB` | `44.536 MB` |
| simulation default | `145.689..165.054 / 146.905 ms` | `220.357 ms` | `11.530..11.531 MB` | `12.108 MB` |
| simulation large | `5.913..6.080 / 5.948 s` | `8.922 s` | `426.403 MB` | `447.723 MB` |
| simulation long-run | `3.577..3.649 / 3.604 s` | `5.406 s` | `47.352..47.358 MB` | `49.726 MB` |

这里的 MB 只用于摘要可读性；baseline 保存原始整数 bytes/nanos。simulation
default 的一个慢样本未形成双峰，公式仍同时受 p50 与 p90 约束，没有人工剔除。

## 3. GC envelope

| Application/profile | Calibration maximum | Baseline envelope |
|---|---:|---:|
| scheduler default | Young `0/0 ms`，Full `0/0 ms` | `0/0`，`0/0` |
| scheduler large | Young `5/17 ms`，Full `0/0 ms` | `6/22 ms`，`0/0` |
| scheduler long-run | Young `1/4 ms`，Full `0/0 ms` | `2/5 ms`，`0/0` |
| simulation default | Young `0/0 ms`，Full `0/0 ms` | `0/0`，`0/0` |
| simulation large | Young `15/11 ms`，Full `1/28 ms` | `16/14 ms`，`2/35 ms` |
| simulation long-run | Young `0/0 ms`，Full `0/0 ms` | `0/0`，`0/0` |

Simulation large 的单次 Full GC 在 9 个 fork 中全部出现，pause 仅占 hot operation
的小比例。它是 256 MiB 固定 heap 下的大 live-set 特征，不是 GC thrash，也没有
证据要求扩大 heap 或修改 SOMA core。

## 4. Baseline Owner 切换

每个 child application 现在分别拥有：

```text
performance-baseline-default-zulu8-macos-aarch64-v2.json
performance-baseline-large-zulu8-macos-aarch64-v1.json
performance-baseline-long-run-zulu8-macos-aarch64-v1.json
```

两个旧 `performance-baseline-zulu8-macos-aarch64-v1.json` 已在六份候选通过后
退役。它们的历史身份由 Git 和最终 Governance Report 保留，不再作为 current
Owner。Architecture Gate 验证 component baseline `1`、application baseline
`6`、public claim `0`，并逐一检查 subject、profile、artifact version 和 fork
责任。

## 5. 独立普通 Gate

在校准之后重新构建并启动新的 3-fork JVM，Fast、Scale 和 Soak 全部通过。
普通 artifact 的 workload/result identity 与校准一致，未自动改写 threshold：

- Fast：scheduler default、simulation default；
- Scale：scheduler large、simulation large；
- Soak：scheduler long-run、simulation long-run；
- Architecture：`component=1`、`reference-application=6`、
  `public-claim=0`。

Stage 3 因此完成。上述结果仍需 Stage 4 的规模增长解释、scope non-regression
和完整项目 Gate，才能进入正式固化。

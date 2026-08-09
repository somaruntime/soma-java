# SOMA V1 交付导向仓库治理

类型：Conformance / Repository Governance Record

状态：`PASS`

日期：2026-08-09

Owner：delivery-centered repository structure、入口与 executable quality infrastructure 的迁移证据

## 1. 结论

本次治理在不改变 V1 Blueprint/Design、public/generated API、production artifact topology 和
release authorization boundary 的前提下，将仓库默认叙事从 I0–I8 实施过程转为产品交付：

- 根 README 首先解释 SOMA（State-Oriented Memory Architecture）、适用场景、能力、边界与快速开始；
- Library user、Codex/Agent、Product Owner/维护者拥有三个清晰入口；
- `project/` 继续保存 Blueprint、Design、Engineering 与 Conformance，但不再主导根 README；
- `config/` 退出，内容按真实 Owner 迁入 `build-support/linkage` 与 `build-support/delivery`；
- scripts 只暴露四个稳定意图，资格 internals 与 tests 改为 capability naming；
- `docs/` 与 `benchmarks/` 只保留 placeholder，内容留给未来独立专题。

## 2. Replacement closure

| 旧 surface | 当前 Owner/surface | 结论 |
|---|---|---|
| root README 中的 I0–I8/Gate 主叙事 | user-first root README；内部状态进入 `project/` | REPLACED |
| `config/linkage` | `build-support/linkage` | MOVED |
| `config/release` | `build-support/delivery` | MOVED |
| `scripts/qualify-i0.sh` | `build-support/qualification/artifact-build.sh` | RENAMED；current narrow proof retained |
| `scripts/qualify-i1..i4.sh` | current module/cumulative evidence；I2–I4 snapshot进入`project/engineering/history` | retired from active execution |
| `scripts/qualify-i5..i7.sh` | `build-support/qualification/<capability>.sh` | RENAMED；coverage retained |
| `scripts/qualify-i8.sh` | `scripts/qualify.sh` | REPLACED |
| `scripts/benchmark-i8.sh` | `scripts/benchmark.sh` | REPLACED |
| `scripts/package-i8.sh` | `scripts/package-local.sh` | REPLACED |
| `scripts/generate-grouped-api.py` | `build-support/codegen/generate-grouped-api.py` | MOVED |
| `tests/i0,i5..i7-*` | `tests/<capability>` 与 `tests/regeneration/<capability>` | RENAMED；current fixtures retained |
| I1 primitive slice-only fixture/golden | current module/cumulative evidence；historical conclusion留在I1记录 | RETIRED after current replay |
| I2–I4 consumer/golden/regeneration | `project/engineering/history/` | PRESERVED AS HISTORICAL SNAPSHOT |
| stale Engineering post-I2 status | completed capability-oriented Engineering entry | REPLACED |

I0–I8 qualification records仍保留阶段名，因为它们拥有实施 provenance；它们不再是 executable
目录 taxonomy。第一次 current replay 证明旧 I1 consumer 依赖已被后续实现替换的 internal
constructor，因此没有继续修补历史 suite，而是退役该 slice-only fixture。I3/I4重放进一步证明
其golden固定的是当时generated surface，因此I2–I4材料整体迁入Engineering history。仍承担current
proof的artifact、module、relation、parallel、compression、scenario与package evidence均保留。

## 3. Stable command contract

| Command | Contract |
|---|---|
| `scripts/check.sh` | 日常 correctness、generated/consumer、Examples 和 local package；跳过百万行 profile |
| `scripts/qualify.sh` | 完整 non-publishing qualification，包含 profile 与 package |
| `scripts/benchmark.sh` | 三个 reference application 的可配置 profile |
| `scripts/package-local.sh` | 本地 artifact、sources/javadocs、SBOM、checksum、provenance、source bundle |

CI 使用 `check.sh`；non-publishing release qualification workflow 使用 `qualify.sh`。完整资格并未
因日常入口减负而丢失。

## 4. Delivery boundary

Source delivery 继续使用显式 allowlist。它包含构建所需的最小 `build-support/linkage`，排除
`project/`、tests、scripts、qualification/codegen/delivery internals、module test source、target 和
Git metadata。新增 module README 随 source delivery 提供 artifact 边界说明。

`CODE_OF_CONDUCT.md` 作为 repository 协作行为合同保留；`NOTICE` 作为 Apache-2.0 distribution
notice 与品牌资产边界保留。二者都不是 runtime configuration。

## 5. 验证

本次迁移按以下顺序验证：

1. shell syntax、codegen check、POM/workflow/path audit；
2. Markdown relative-link 与 stale active-path audit；
3. Java 8 `scripts/check.sh`，覆盖 reactor、generated surface、independent consumers、Examples、
   local package、SBOM/checksum/provenance 与 source-delivery exclusion；
4. `git diff --check` 与 no committed build artifact 检查。

2026-08-09 当前 checkout 的实际结果：`scripts/check.sh` 退出码 `0`；runtime 55 tests、processor
34 tests 均无 failure/error；compression/metadata cumulative consumer、三个 reference application、
local package、checksum、SBOM、source bundle 与 packaged scheduling consumer 均为 `PASS`。
迁移后的 `artifact-build` narrow qualification同样为`PASS`。旧I1 wrapper暴露internal constructor
drift，旧I3/I4 wrapper暴露point-in-time generated golden drift；两者均触发上述replacement
closure，而不是修改production code或增加compatibility surface。

完整百万行 profile 未因目录迁移重新执行：profile implementation、scenario、JVM 参数和 production
code 均未改变，日常治理验证不重复消费高成本 G9 证据。可重放入口仍为 `scripts/qualify.sh` 或
`scripts/benchmark.sh`。

## 6. Claim boundary

本记录证明 repository structure、入口与已有 executable evidence 的迁移闭合；不重新证明或扩大
I0–I8/G1–G10 产品能力，不构成正式 release、远端 publication、签名、一亿行性能承诺或跨硬件 SLA。

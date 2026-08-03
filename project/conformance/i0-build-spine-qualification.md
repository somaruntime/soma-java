# SOMA Java V1 I0 Build Spine Qualification

类型：Conformance Evidence

状态：`PASS`

Owner：I0 build spine、artifact、full-regeneration carrier 与相应 G1/G2/G10 evidence

资格日期：2026-08-03

## 1. 结论

I0 已在真实 Java 8 Maven 边界建立并验证：

- exactly `soma-runtime` + `soma-processor` 两个 production artifact；
- runtime classpath 与 processorpath 分离的 independent consumer；
- aggregating JSR 269 composition carrier、full-source-set handshake、manifest-last publication、
  deterministic generation 与删除/重命名 stale cleanup；
- processor/runtime compile-time 与 runtime linkage version fail-closed；
- shared configuration carrier 的 configure-first/default-first ordering；
- source/javadoc classifier、LICENSE/NOTICE、provenance、zero-production-dependency 与静态 SBOM
  baseline。

因此 I0 状态为`COMPLETE`，G1 为`PASS`。G2 与 G10 只记录 I0 范围证据，整体仍分别由 I1-I2
与 I8 继续关闭；G5 不因 configuration carrier evidence 提前运行。

## 2. 被测输入与环境

| 项目 | 证据 |
|---|---|
| Implementation baseline | `08df08500e5ae49623f844b96e25cd1d503b29fb` |
| Review correction | `841964d139400c79052dfaa3952a715e7b1f9fb8` |
| Qualification commit | `841964d139400c79052dfaa3952a715e7b1f9fb8` |
| Dirty state | `false` |
| Command | `./scripts/check-i0.sh` |
| JDK | OpenJDK `1.8.0_502` |
| Maven | Apache Maven `3.9.16` |
| Maven repository mode | Java user-home cache；public Maven Central host configuration |
| OS / arch | Darwin `25.6.0` / `arm64` |
| CPU / memory | Apple M5 Pro / `51539607552` bytes |
| JVM max heap | `11453595648` bytes |

本次 qualification 使用已准入、已缓存的 standard Apache Maven build-plugin graph。它证明
clean reactor 与产品边界，不把公网 cold-download 可用性外推成产品正确性；需要单独验证空
cache repository provenance 时可显式设置`SOMA_I0_MAVEN_REPOSITORY`。

## 3. Artifact fingerprint

| Artifact | SHA-256 |
|---|---|
| `soma-runtime-1.0.0-SNAPSHOT.jar` | `9d2d840dab79966f664d8ea1bdc61e882773d80d66c5480de8ac988b29003b2e` |
| `soma-processor-1.0.0-SNAPSHOT.jar` | `39a517209f8d14cbf172976cf1aa96c3a523a5978a848478f13b433273767ab7` |
| independent consumer composition manifest | `57f4d29bc239357f4e561927e483ad252ee985a27f10b70e11072d118c979b7f` |

Qualification 还逐一比较两个 clean build 的 binary/source/javadoc 六个 JAR 摘要；两次结果
完全一致。Generated source、class、JAR 与完整日志位于 ignored build output，不进入 Git。

## 4. Evidence matrix

| Proof | Positive evidence | Negative / failed-state evidence | 结果 |
|---|---|---|---|
| Maven/artifact topology | clean reactor；两个 module 各产生 binary/source/javadoc | root aggregator 不安装为第三 artifact；artifact class allowlist | PASS |
| Java 8 | real JDK 8 compile/run；`javap` major version 52 | 非完整 JDK/非 Java 8 在脚本入口拒绝 | PASS |
| Processor isolation | external consumer 的 runtime classpath 与 processorpath 分离 | runtime JAR 无 processor/service；processor JAR 无 runtime annotation classes | PASS |
| Full regeneration | fresh、rename、delete、plain lifecycle replay | stale generated source/class/manifest member 必须消失 | PASS |
| Determinism | source input 顺序置换；两个 clean build | manifest 无 timestamp/absolute path | PASS |
| Composition failure | valid schema 与 Unicode schema round-trip | missing handshake、empty composition/Table、explicit member、invalid role/capacity、reserved namespace、FQN collision、late round | PASS |
| Manifest contract | manifest 最后发布；Java 8 `Properties.load(InputStream)` Unicode round-trip | validation failure 不得产生任意 composition carrier 或 success manifest | PASS |
| Version linkage | matching runtime/processor compile/run | compile-time mismatch 与 packaged runtime swap fail closed | PASS |
| Configuration carrier | class-load/observe/build-only/configure-first/default-first | null/repeated/invalid configuration 与 constructor/public-surface negative | PASS |
| Classifier/delivery | source/javadoc/LICENSE/NOTICE/provenance/SBOM | runtime internal package 不进入 public Javadoc；no committed generated/build output | PASS |
| Dependency | runtime/processor dependency tree | zero production dependency | PASS |
| Test hygiene | qualification-owned temp root | processor harness temporary directories 由 qualification trap 回收 | PASS |

Failed-state 的精确边界仍按 I0 M1 clarification：validation 前失败不写 composition carrier；
late-round或后续 javac 失败可以留下不可消费的 physical output，但没有 success manifest，且下一次
qualified Maven lifecycle 首先完整清理 target。

## 5. Independent review

独立只读审查在 baseline commit 上重放完整 qualification，发现并实证四项缺口：Java 8
properties Unicode 编码、failed-carrier helper 的固定路径、processor harness 临时目录泄漏、
runtime internal Javadoc 排除失效。`841964d`逐项修复并加入防回归证据。

针对 corrective delta 的定向独立复核结论为`APPROVE`：审查者只检查上述四项 delta，定向执行
Maven package、Java 8 `I0ProcessorHarness`与 artifact prefix 检查，未重跑完整 qualification，
未修改文件；限定范围内无 P0/P1/P2 遗留。

## 6. Gate projection

| Gate | I0 后状态 | 边界 |
|---|---|---|
| G1 Artifact/build/full regeneration | `PASS` | I0 最终 Owner 已关闭 |
| G2 Schema/generated surface | `IN_PROGRESS — I0_SCOPE_PASS` | 完整 generated public API/type/Key/Index matrix 在 I1-I2 |
| G10 Security/package/release | `IN_PROGRESS — I0_BASELINE_PASS` | dependency/license/provenance/SBOM baseline；最终 package/CI/release 在 I8 |
| G3-G9 | `NOT_RUN` | 对应 production surface 尚未完成；G5 尤其不能由 config carrier 代替 |

## 7. Claim boundary

本记录只证明 I0 build/generation carrier。当前仍没有 I1+ public generated Table API、Table
storage/query/mutation、performance qualification、CI/release workflow、published Maven Package
或 GitHub Release。不得从 G1 PASS 推导完整产品可用性、兼容性、性能或 release readiness。

Evidence-only 状态提交不改变上述被测 production input；I1 仍为`NOT_STARTED`。

# SOMA Java I0 Build Spine Qualification

类型：Conformance / Implementation Slice Qualification

状态：`PASS`

Slice：`I0 COMPLETED`

Gate disposition：`G1 PASS`；`G2 I0_SCOPE_PASS / IN_PROGRESS`；
`G10 I0_SCOPE_PASS / IN_PROGRESS`

正式事实源：是（I0 implementation、artifact、full-regeneration与资格证据）

Owner：SOMA Java I0 build spine current executable fact

资格日期：2026-08-04

## 1. 结论

I0 已建立后续 V1 实施所需的真实 Java 8 build/generation boundary，并满足
[Implementation Plan](../engineering/v1-implementation-plan.md#4-i0--build-spine)的 I0 Exit：

- root 是不发布的 Maven reactor；正式 production artifact 恰好为 `soma-runtime` 与
  `soma-processor`；
- 六个 schema annotation、aggregating processor、full-source-set handshake、late-round
  collection、manifest-last publication 与 clean full-regeneration 已形成 production baseline；
- independent Java 8 consumer 在分离 classpath/processorpath 下编译并运行；
- processor/runtime 通过 artifact SHA-256 建立 exact linkage；同版本但不同 runtime binary
  fail closed；
- internal configuration Owner 的 observe/configure/default freeze 顺序通过并发测试；
- Java 8 bytecode、JUnit test-only 隔离、source/javadoc/legal files、dependency/SBOM/security
  baseline与可重复构建证据成立；
- 代码、测试与资格脚本通过三条只读独立审查线，未留下 blocker 或 P1 finding。

I0 没有生成 I1 才拥有的公开 `Soma`、`SomaGroup`、typed Table/storage surface，也没有证明
storage、query、mutation、relation、parallel、compression、million-row performance、CI 或
release qualification。因此本记录只关闭 I0，不把 scoped evidence 外推为完整 V1 或 release
claim。

## 2. 冻结输入与工作树政策

| 项目 | 冻结事实 |
|---|---|
| Repository / branch | `somaruntime/soma-java` / `develop` |
| I0 base | `a09c591016e6be9ec6b79c97c69eca95784583ee` |
| I0 implementation commit | `62320cba611996ac3d80bc05a5fc156f4aef9cc6` |
| Production artifacts | `io.github.somaruntime.soma:soma-runtime:1.0.0-SNAPSHOT`；`io.github.somaruntime.soma:soma-processor:1.0.0-SNAPSHOT` |
| Build aggregator | `io.github.somaruntime.soma.build:soma-reactor:1.0.0-SNAPSHOT`；install/deploy 均 skip，不是第三个 published artifact |
| Evidence input | `a09c591..62320cb` 的全部 implementation bytes |
| Dirty-state policy | 最终全量资格在 implementation bytes 暂存后执行；随后提交未改变这些 bytes。Conformance 状态文档由下一独立提交拥有 |
| Generated output policy | `target/`、consumer classes与generated sources不提交；clean lifecycle 是 stale cleanup Owner |

没有复制、cherry-pick、包装或恢复 predecessor source；active reactor没有 legacy module、第三
production module或未来 slice placeholder。

## 3. 资格环境

| 维度 | 值 |
|---|---|
| OS / arch | macOS Darwin `25.6.0` / `arm64` |
| CPU / memory | 18 processors / 48 GiB physical memory |
| JDK | Amazon Corretto `1.8.0_502-b07`, 64-bit Server VM |
| `javac` | `1.8.0_502` |
| Maven | Apache Maven `3.9.16` |
| JVM observed max heap | approximately 10.67 GiB |
| Repository route | public Maven Central mirror；company-internal Nexus disabled in user settings |

I0 不提交 Maven Wrapper。当前可重放合同是 system Maven `3.9.x` + qualified Java 8，并由
Maven Enforcer 与 `scripts/qualify-i0.sh`拒绝其他 major/minor contract。它证明当前环境与已固定
plugin 组合，不声称所有 Maven 3.9 patch/JDK 8 distribution 已经形成支持矩阵。

## 4. 可重放入口与结果

Canonical command：

```sh
./scripts/check.sh
```

`check.sh`解析并固定 Java 8 `JAVA_HOME` 后进入 `scripts/qualify-i0.sh`。最终执行结果：

```text
soma-runtime tests:   8 run, 0 failures, 0 errors, 0 skipped
soma-processor tests: 10 run, 0 failures, 0 errors, 0 skipped
independent consumer: compiled and ran
same-version mismatch negative: rejected with [SOMA-0001]
real Maven rename/deletion regeneration: passed
two clean package artifact sets: byte-for-byte SHA-256 equal
i0-qualification: ok
```

资格脚本还验证：

1. reactor只列出两个production module，child POM自包含，root install/deploy skip；
2. main、source、javadoc共六个artifact均生成，main/source包含`LICENSE`与`NOTICE`；
3. production JAR不包含JUnit，processor service registration只存在于processor artifact；
4. runtime/processor class major version均为52；
5. consumer使用runtime classpath与`processor + runtime` processorpath，不能依赖reactor classpath
   偶然成功；
6. 同一fixture先生成`Alpha`，再以clean full lifecycle改为`Gamma`；旧class和旧manifest member
   消失，新输出出现；
7. main/source/javadoc六个artifact经过第二次clean package后哈希完全一致。

## 5. I0 机制与 failed-state 证据

### 5.1 Schema 与 generation carrier

- 正式 annotation inventory 恰好为 `@SomaSchema`、`@SomaTable`、`@SomaValue`、
  `@SomaField`、`@SomaKey`、`@SomaIndex`，统一 `RetentionPolicy.CLASS`；
- processor跨round聚合，在`processingOver`时生成；没有`-Asoma.fullSourceSet=true`时稳定拒绝；
- package-level composition只收集直接 package 中的top-level Table；reachable Value递归发现，
  orphan Value不进入manifest；
- 输入顺序不改变generated source/manifest；Unicode properties经过Java 8 round-trip；
- empty/invalid/cycle/collision/partial-source-set negative均不能形成valid carrier/manifest；
- generation先完成全模型验证和内存render，再写source，success manifest最后写；注入I/O失败时
  不发布可被runtime接受的composition。

### 5.2 Exact runtime linkage

`config/linkage/linkage.properties`是processor/runtime contract version的唯一repository Owner。
Runtime从自身defining artifact读取该resource，并对自身JAR或排序后的class directory计算
SHA-256；processor把所编译runtime的version、contract和artifact identity写入internal carrier。
Carrier class initialization自动调用linkage check，不依赖application手动验证。

资格脚本在不改变version的情况下修改runtime JAR，consumer启动稳定失败为`[SOMA-0001]`；测试
classpath中伪造的同名resource不能覆盖defining artifact resource。

### 5.3 Shared configuration Owner

`RuntimeConfigurationOwner`是当前唯一production配置状态Owner：observe不freeze；configure-first
只允许第一次发布；default-first原子freeze默认值；并发configure/default race只有一个稳定获胜者。
公开`Soma`、default Group/Table和metadata observation仍分别属于I1/I7，不由I0预建。

## 6. Dependency、license、security 与 trust baseline

Production dependency只有`processor -> runtime`；runtime无production third-party dependency。
JUnit Jupiter固定为`5.11.4`且只处于test scope，未引入Vintage，也未泄漏到main artifact或
independent consumer runtime。

| Component | Scope | Package URL | License |
|---|---|---|---|
| soma-runtime 1.0.0-SNAPSHOT | production | `pkg:maven/io.github.somaruntime.soma/soma-runtime@1.0.0-SNAPSHOT` | Apache-2.0 |
| soma-processor 1.0.0-SNAPSHOT | production | `pkg:maven/io.github.somaruntime.soma/soma-processor@1.0.0-SNAPSHOT` | Apache-2.0 |
| junit-jupiter 5.11.4 | test | `pkg:maven/org.junit.jupiter/junit-jupiter@5.11.4` | EPL-2.0 |
| junit-jupiter-api 5.11.4 | test | `pkg:maven/org.junit.jupiter/junit-jupiter-api@5.11.4` | EPL-2.0 |
| junit-jupiter-params 5.11.4 | test | `pkg:maven/org.junit.jupiter/junit-jupiter-params@5.11.4` | EPL-2.0 |
| junit-jupiter-engine 5.11.4 | test | `pkg:maven/org.junit.jupiter/junit-jupiter-engine@5.11.4` | EPL-2.0 |
| junit-platform-commons 1.11.4 | test | `pkg:maven/org.junit.platform/junit-platform-commons@1.11.4` | EPL-2.0 |
| junit-platform-engine 1.11.4 | test | `pkg:maven/org.junit.platform/junit-platform-engine@1.11.4` | EPL-2.0 |
| opentest4j 1.3.0 | test | `pkg:maven/org.opentest4j/opentest4j@1.3.0` | Apache-2.0 |
| apiguardian-api 1.1.2 | test | `pkg:maven/org.apiguardian/apiguardian-api@1.1.2` | Apache-2.0 |

[JUnit 5.11.4 User Guide](https://docs.junit.org/5.11.4/user-guide/junit-user-guide-5.11.4.pdf)
声明Java 8或更高版本。2026-08-04对上述八个third-party Maven GAV执行OSV官方
`https://api.osv.dev/v1/querybatch`查询，结果均为空。该结论只表示查询时没有OSV已知匹配，
不是永久安全保证；完整交付SBOM、workflow与release qualification仍由I8/G10拥有。

Annotation processor是consumer build期间执行的trusted build-time code；I0不宣称processorpath
可隔离恶意processor、修改后的bytecode、agent、`Unsafe`或有意破坏的JVM进程。

## 7. Artifact provenance

以下artifact由同一I0 implementation input和canonical qualification生成；size单位为byte：

| Artifact | Size | SHA-256 |
|---|---:|---|
| `soma-runtime-1.0.0-SNAPSHOT.jar` | 22094 | `b37daeafadee3627e0ed7240ac74802955f10296ad4471b9e3a49c2897bc30a5` |
| `soma-runtime-1.0.0-SNAPSHOT-sources.jar` | 14682 | `d1470c9e42e2ff8e2025ed4d11d60031b0efa35b8becdd11f71af821f62437aa` |
| `soma-runtime-1.0.0-SNAPSHOT-javadoc.jar` | 50131 | `4a2c4f941913694b3446a8c567b392312d0c59a11fbccdadadef3a72b1fb29b0` |
| `soma-processor-1.0.0-SNAPSHOT.jar` | 27830 | `aec47e4dbb4a503b29d9a63101d26c2ecec39ce1c586320756e5b56f7498eba5` |
| `soma-processor-1.0.0-SNAPSHOT-sources.jar` | 16518 | `7c6cfbab2c8922390f34b6e4ddea2d032e958cc7b53da063fc92470107ab3e1e` |
| `soma-processor-1.0.0-SNAPSHOT-javadoc.jar` | 24185 | `99de7289ad45c270071be77f4e8f7eb9c389c1e77de7a19628122686274b52cc` |

Repository legal-owner digests：

- `LICENSE`：`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`；
- `NOTICE`：`bec11e5bc62b17b14119b29248d3803af0ddb4e8bedba20b933b7594619581f0`。

这些是qualification-local artifact fingerprint，不是发布坐标、签名或remote publication。

## 8. 独立审查

三条只读审查线针对I0 implementation分别完成复核：

| Reviewer route | Scope | Verdict |
|---|---|---|
| `compiler_audit` | aggregating rounds、model validation、determinism、manifest/full regeneration、diagnostics | `PASS`；此前5个P1全部关闭，无新blocker/P1 |
| `runtime_audit` | artifact identity/linkage、configuration CAS/freeze、API leakage与failed-state | `PASS`；P0/P1全部关闭 |
| `product_evidence_audit` | I0 Exit、G1/G2/G10 claim boundary、package/dependency/security evidence | `PASS`；无blocker |

三条审查所列P0/P1均在最终资格前关闭，随后再次执行canonical qualification；没有用重复审查
替代新evidence，也没有因审核通过而外推未实现能力。

## 9. Gate disposition 与剩余边界

| Gate | I0 disposition | 说明 |
|---|---|---|
| G1 | `PASS` | two artifacts、Java 8 clean build、independent consumer、full regeneration、exact linkage、classifiers与provenance baseline已闭合 |
| G2 | `I0_SCOPE_PASS / IN_PROGRESS` | six annotation与internal composition carrier成立；I1-I2公开generated surface/type matrix尚未实现 |
| G10 | `I0_SCOPE_PASS / IN_PROGRESS` | dependency/license/OSV/JAR/legal/checksum/reproducibility baseline成立；CI、完整SBOM、package consumer与release qualification属于I8 |
| G3-G9 | `NOT_RUN` | 对应production surface尚未出现 |

I0完成后没有active slice。下一项只允许从I1 primitive keyed Table vertical slice开始；I1必须
自行完成exit evidence、独立审查、Conformance更新与干净提交，不能把本记录当成I1证据。

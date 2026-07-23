# 实施与原子切换设计

类型：Temporary

状态：active

Owner：SOMA 参考应用实施与切换设计

事实范围：可独立保留的实施切片、Maven 中间态、benchmark 迁移顺序、最终原子切换与回滚边界

非事实范围：SOMA public/generated API、正式 Design、最终 Gate 结论或 release

最后审查日期：2026-07-23

## 1. 不制造 legacy module

起始 `soma-examples` 是仍承担 G5 的 JAR。立即把它改成 aggregator 只有两种做法：先删除 evidence，或把旧代码搬进短命 `legacy-scenarios`。前者违反非回归，后者制造重复路径和两次无产品收益的搬迁。

因此采用如下过渡：

```text
Stage 2–4
soma-examples/                  current legacy JAR remains executable
├── pom.xml
├── src/                        old four scenarios
├── industrial-dynamic-scheduler/   independent POM, not inherited
└── grassing-individual-simulation/ independent POM, not inherited

Stage 5 atomic cutover
soma-examples/                  packaging=pom aggregator
├── pom.xml
├── industrial-dynamic-scheduler/
└── grassing-individual-simulation/
```

子项目在 Stage 2 起由专用 isolated Gate 构建，不依赖顶层旧 POM。Stage 5 才把它们登记为 aggregator modules，并在同一提交删除旧 `src/docs/reports` 与旧 Gate。

## 2. 独立 consumer POM

两个 child POM 不继承 SOMA root/aggregator parent，显式拥有：

- application 自己的 `groupId/artifactId/version`；
- `soma.version=0.2.0-SNAPSHOT`；
- Java 8 source/target、compiler/enforcer/plugin exact version；
- `soma-annotations`、`soma-runtime-core` compile dependency；
- `soma-processor` provided dependency和 annotation processor path；
- `maven.deploy.skip=true`；
- 无 `soma-testkit`、other-example 或 third-party dependency。

checker 比较 child `soma.version` 与 root candidate version，防止示例悄悄消费其他版本。最终 aggregator 只聚合，不向 child 注入 dependencyManagement 或 reactor-only property。

## 3. Artifact-isolation Gate

`check-reference-applications.sh` 的每次 clean run：

1. 建立 evidence-local Maven repository；
2. 只把 root parent、annotations、runtime-core、processor 以 candidate shape 安装进去；
3. 分别用 `-f <child>/pom.xml` 启动 Maven，构建、生成、测试编译；
4. 检查 runtime dependency graph 含 runtime-core、不含 processor；
5. 在仅有 child classes + resolved runtime classpath 下执行 main/verification；
6. clean/repeat 比较 schema/hash、generated-source manifest 和 input checksum；
7. 检查 class major 52、禁止 internal/testkit/other-example import；
8. 保存环境、命令、artifact checksum 与 `claimAllowed=false`。

该 Gate 证明依赖可解析和应用可运行；root reactor build 只证明仓库集成，两者不能互相替代。

## 4. Benchmark 迁移

迁移按 evidence Owner 而不是文件名进行：

### 4.1 Neutral component schema

`soma-benchmarks` 增加自己拥有的 schema：

- keyed/grouped `CandidateFact`：key、group exact source、filter/sort/best/update/remove；
- dense `DenseFact`：packed scan、replace/sort、ColumnView；
- keyed `LookupFact`：primary/unique/exact lookup；
- parent/child `OwnerFact`：child locality/replacement。

现有 smoke/component lane 保留 lane id、artifact v4 和 validator 语义，只替换领域 carrier。迁移前后使用相同 row count、cardinality、checksum 与 operation shape。

### 4.2 Integrated lane

- 旧 FJSP 100k lane 在 scheduler correctness/benchmark 接管前继续由旧 example 提供；
- scheduler application 建立自己的 workload artifact 后，旧 FJSP runner/report/options/generator 整体退役，不搬进 neutral benchmark；
- simulation integrated lane由仿真应用自己拥有；
- Stage 5 删除 `soma-benchmarks -> soma-examples` dependency，并由 source-shape Gate 禁止恢复。

这不是保留长期双轨：旧 integrated lane 只有在替代 lane 通过前有效，账本逐项记录 Owner handoff。

## 5. 实施切片

| Slice | 变更 | 独立正确性 |
|---|---|---|
| S2.1 | 两个 child standalone POM、目录、配置/CLI骨架、isolation Gate | 不影响旧 JAR/G5，普通 consumer build成立 |
| S2.2 | benchmark neutral schema和component lane迁移 | lane语义/artifact/checksum不变，旧 integrated lane继续 |
| S3.1 | scheduler config/generator/problem/input checksum | 不依赖 SOMA runtime，可单独重放 |
| S3.2 | scheduler schema/bootstrap/aggregate | projection equivalence与lifecycle通过 |
| S3.3 | scheduler engine/validator/config suites | 完整约束、long-run、多fork通过 |
| S4.1 | simulation config/generator/initial checksum | 不依赖 SOMA runtime，可单独重放 |
| S4.2 | simulation schema/bootstrap/systems/AoS | tiny逐tick、order independence通过 |
| S4.3 | simulation long-run/multifork | numeric/churn/lifecycle通过 |
| S5.1 | replacement ledger审查 | 每项旧产品责任已有唯一Owner |
| S5.2 | aggregator、旧代码/fixture/script删除、benchmark dependency删除 | code/build原子切换 |
| S5.3 |正式文档/current Report/checker原子切换 | Owner/导航/G5原子切换 |
| S6 | full validation、Report、Temporary退役 | immutable closeout |

任何 slice 都不需要后续 rewrite 才满足其当时声明；最终目标在 S5 以前仍由 Temporary 明确持有。

## 6. Failure 与回滚

- child app失败只丢弃其未发布 runtime aggregate，不回写 generator input；
- benchmark lane迁移失败保留旧 lane与依赖，不修改 artifact schema；
- Stage 5 只有在新应用、neutral benchmark、reference closure全部通过后开始；
- Stage 5 若 Gate失败，修复同一 candidate，不能恢复平行 current Owner；
- Git commit 是阶段不可变点，但不使用 destructive reset；保留每个已通过 slice。

## 7. 最终检查

最终 root reactor包括 aggregator及两个 child；`soma-examples` 不再产出领域共享 JAR。`soma-benchmarks` runtime graph不含 examples。两个 child isolated build与root build都通过，但应用的 canonical consumer claim来自前者。

# soma_java 文档体系重构收尾报告

状态：正式治理收尾报告
日期：2026-07-10
Owner：根项目协调层

## 1. 收尾结论

本次“代码实施前最后一次文档体系重构”已经完成：

- 根项目和六个模块的正式设计事实均进入各自 `docs/`；
- 正式设计文档全部登记唯一 Owner，不再存在联合 Owner；
- README、AGENTS、guides、reports、temp 的权威边界已经固定；
- deep materialization、Generated Table API、schema processing、runtime storage/lifecycle、benchmark methodology/scenario 已按职责拆分；
- 四份长期研究蓝图全部保留，明确标记为非正式事实源；
- 历史治理报告已归档；
- 文档自动检查和 Maven reactor validate 已通过。

本结论只证明文档体系治理完成，不表示 Java 功能、package smoke、benchmark 或 V1 release gate 已完成。

## 2. 最终文档分层

```text
README / AGENTS
  -> navigation and operating guardrails

root docs
  -> cross-module constitution, architecture, API, models, strategy and gates

module docs
  -> module-owned contracts

guides
  -> future user/developer/contributor guidance

reports
  -> dated evidence snapshots

docs/temp
  -> drafts and explicitly retained long-lived blueprints
```

## 3. 正式 Owner 结构

当前共有 25 份正式 owner document：

- 根级 10 份；
- module-owned 15 份；
- 正文约 6,300 行，不含 docs README；
- 没有超过 500 行的正式文档。

超过 350 行的两份文档已经复审：

- `soma-annotations/docs/annotation-schema-contract.md`：475 行，属于 exhaustive public annotation reference，仍保持单一职责；
- `soma-examples/docs/fjsp-runtime-state-example.md`：366 行，长代码来自完整 FJSP schema source；dispatch/E2E 已拆入独立文档。

## 4. 关键拆分

### 4.1 根级

- 原 Row Pipeline 综合文档重构为 `Generated Table API 契约`；
- 新增独立 `Materialization 契约`；
- 设计宪法只保留总心智模型和永久原则；
- 架构文档只保留结构、模块、依赖与数据流；
- correctness/performance/implementation strategy 移除 scenario 或 module-owned 细节。

### 4.2 annotations / processor

- annotations 只拥有 public declaration；
- normalized schema、exact hash、compatibility 和 diagnostics 迁入 processor `Schema processing 契约`；
- code generation 独立拥有 artifacts、static binding、golden 和 package smoke；
- Generated Table public semantics 由根级 contract 拥有。

### 4.3 runtime-core

- `TableStore 契约` 拥有 storage components；
- `Runtime lifecycle 契约` 拥有 ownership、mutation、view/epoch、errors、concurrency 和 release；
- `Runtime 性能实现契约` 独立拥有 packed/primitive/fused/allocation-bounded discipline。

### 4.4 benchmarks / examples

- benchmark methodology/claim 与 runtime-state scenario lanes 分开；
- FJSP schema 与 E2E flow 分开；
- VRP、Simulation、Game 保持独立 formal scenario；
- Access Pattern Card 继续属于 examples/scenario/runtime plan，不进入 Schema/hash。

## 5. V1 防缩水复审

重构后仍明确保留：

- Java 8、Java-only；
- keyed/dense table；
- immutable `@SomaValue`；
- schema class 作为 detached materialized row；
- `List<R>`/dense 与 `Map<K,R>`/keyed；
- parent-owned child，no share/no reparent/cascade lifecycle；
- primitive columns、presence bitmap、packed rows；
- KeySpace、index、unique、order、dynamic sort；
- Direct API、Row/Key/Column Pipeline、Mutator、ColumnView；
- recursive materialization、budget、all-or-nothing；
- floating strict identity/access policy；
- synchronous single-owner concurrency boundary；
- no serialization/persistence；
- typed errors、schema hash/runtime compatibility；
- compile/golden/correctness/performance-shape/package/benchmark/release gates。

没有把 development phase 的暂缺能力改写为 V1 非目标。

## 6. Blueprint 与报告治理

以下四份蓝图保留在 `docs/temp/`：

- FJSP MachineCandidate frontier；
- VRP runtime frontier；
- Simulation runtime state；
- Game runtime frontier。

每份蓝图已经声明：

- 长期研究状态；
- 非正式事实源；
- 已固化 owner；
- 仍在研究的问题；
- 最后审查日期。

四蓝图审查报告和此前治理报告迁入 `reports/archive/`。没有实际报告的模块不再保留空 reports scaffold；未来 gate 产生 evidence 时按 validation-gates 指定路径创建。

## 7. Guides 决策

未来用户指南、开发者指南和贡献指南进入 `guides/` 或 module-local `guides/`，不进入 `reports/`。

Guide 只解释当前正式契约，不拥有 API/default/error/compatibility/performance facts。当前尚无 Java implementation，因此没有创建空 guide。

## 8. 自动治理

新增 `./scripts/check-docs.sh`，检查：

- Markdown relative links；
- trailing whitespace 与 fenced code block；
- formal metadata；
- exactly-one Owner 与 no joint Owner；
- formal docs index completeness；
- formal-to-temp dependency；
- migration placeholder；
- 500-line hard limit；
- long-lived blueprint status。

## 9. 验证记录

已通过：

```text
./scripts/check-docs.sh
sh -n scripts/check-docs.sh
git diff --check
JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home \
  mvn -Dmaven.repo.local=../.m2-temp -q validate
```

同时完成：

- 旧 owner filename/path 扫描；
- formal placeholder 扫描；
- exactly-one-owner 扫描；
- V1 critical capability coverage review；
- 四份长期蓝图 metadata review；
- 正式文档行数与职责复审。

## 10. 未执行事项

- 未实现 Java source；
- 未运行 compile/package/runtime tests；
- 未运行 benchmark；
- 未声明性能优势或 release readiness；
- 未 stage、commit 或 push。

## 11. 最终判断

当前文档体系已经具备进入代码实施的结构条件：正式事实可定位、模块 Owner 单一、跨模块契约分离、临时研究与证据报告不再污染设计事实，且后续文档漂移有自动门禁。

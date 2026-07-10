# FJSP MachineCandidate frontier 文档治理专题报告

状态：正式治理报告
日期：2026-07-07
Owner：根项目协调层

## 1. 治理目标

本次治理将 FJSP canonical scenario 从“每轮重建候选 workspace”的文档口径，统一调整为 `MachineCandidate` keyed runtime frontier 口径。

治理目标：

- 把 `runtime frontier` 纳入项目级建模术语；
- 把 FJSP schema 示例统一为 `MachineCandidate` keyed table；
- 把 E2E、correctness、performance、processor/codegen、validation gate 和 benchmark 缺口同步到同一口径；
- 清理正式文档和正式报告中的旧 FJSP canonical 名称；
- 明确剩余风险属于 benchmark evidence 和后续实现证据，不再属于 schema/API 设计歧义。

## 2. 阶段执行

### 阶段 1：项目级事实源和术语

修改文件：

- `docs/architecture-design.md`
- `docs/domain-glossary.md`

处理结果：

- 新增 `runtime frontier` 作为建模场景；
- 明确它不是第三种 table kind；
- 明确有稳定候选身份、需要按 key 删除或按 secondary index 查找的 frontier 应建模为 keyed table；
- 将 FJSP `MachineCandidate` 定义为该场景的典型例子。

### 阶段 2：FJSP schema 和 E2E 契约

修改文件：

- `soma-annotations/docs/annotation-schema-contract.md`
- `soma-examples/docs/runtime-state-schema-examples.md`
- `soma-examples/docs/fjsp-e2e-scenario.md`

处理结果：

- 新增 `Material` / `MaterialId`；
- 新增 `MachineCandidate` schema；
- `MachineCandidate` 使用包含 `MachineId` 与 `OperationKey` 的 stable candidate identity；
- `MachineCandidate` 声明 `by_machine` 和 `by_operation` 两个稳定 access path；
- FJSP dispatch rule 不再通过 schema order 固化；
- E2E 主流程改为 operation release -> frontier add -> per-machine indicator update -> dynamic sort -> assignment -> frontier cleanup。

### 阶段 3：correctness / performance / codegen / gate 口径

修改文件：

- `docs/runtime-correctness-model.md`
- `docs/runtime-performance-model.md`
- `docs/row-pipeline-api-contract.md`
- `docs/validation-gates.md`
- `soma-processor/docs/processor-codegen-contract.md`
- `soma-benchmarks/docs/README.md`

处理结果：

- FJSP oracle 改为 frontier oracle；
- FJSP performance mapping 增加 `MachineCandidate.addBatch`、indicator `update`、dynamic sort、`remove` cleanup lane；
- Row Pipeline 示例改为 candidate indicator update 和 frontier cleanup；
- G2 golden expectation 增加 `MachineCandidate.findByMachine`、`MachineCandidate.findByOperation`、`Machine.byAvailableTime`；
- G5 gate 改为 FJSP frontier E2E smoke；
- benchmark 缺口新增 frontier diagnostic lane。

### 阶段 4：报告和收口

修改文件：

- `soma-examples/reports/fjsp-canonical-scenario-review.md`
- `reports/fjsp-frontier-document-governance-report.md`
- `reports/README.md`

处理结果：

- 重写 FJSP canonical scenario review，避免历史旧正文继续误导后续治理；
- 新增本治理专题报告；
- 更新根级报告索引。

## 3. 设计结论

本次治理后的正式口径：

- `MachineCandidate` 是 keyed runtime frontier，不是 dense workspace；
- row 存在表示候选有效，不使用长期 `active` 字段；
- operation 被选中后，使用 `findByOperation(operationKey).remove()` 删除全部相关候选；
- dispatch rule 属于 solver/application strategy，不在 schema 中声明 `byMachineDispatchRule`；
- comparator 只读取 candidate row 上已计算好的 indicator 字段，不在排序期间做 cross-table lookup；
- setup time 依赖当前 `Machine.lastSetupFamily`，在 dispatch 前按 machine 局部计算；
- SOMA V1 仍只保证单表 mutation 后的内部不变量，跨 table commit 顺序、失败处理和补偿策略归 solver loop。

## 4. 检查结果

已执行一致性搜索：旧 FJSP workspace class name、table name、grouped SPT order method name 和旧 workspace 流程描述在非临时 Markdown 中均无命中。

已执行形态检查：

```text
git diff --check
```

结果：exit 0。

已执行 Maven 验证：

```text
JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home mvn -Dmaven.repo.local=../.m2-temp -q validate
```

结果：exit 0。

## 5. 剩余风险

- `soma-benchmarks` 仍缺正式 benchmark evidence contract；
- frontier diagnostic benchmark 尚未实现，当前只能声明文档口径已统一，不能声明性能优势；
- G2 golden 和 G5 smoke 仍需后续实现证据证明；
- dense workspace 仍是 V1 通用能力，但不再作为 FJSP canonical candidate path。

## 6. 结论

本次文档治理已把 FJSP canonical scenario 从旧 workspace 方案统一迁移到 `MachineCandidate` keyed runtime frontier 方案。正式设计文档、examples 契约、runtime correctness/performance model、processor/codegen 契约、validation gate 和正式审查报告已经对齐。剩余工作进入实现与 benchmark evidence 阶段，不再是文档事实源冲突。

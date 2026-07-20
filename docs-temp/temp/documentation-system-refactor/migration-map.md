# 旧体系到候选体系迁移映射

类型：Temporary

状态：切换方案已裁决，待执行

Owner：SOMA Java 文档体系重构专题

事实范围：现有文档类别到候选分类、物理入口和生命周期动作的迁移规则

非事实范围：逐项事实复核明细、正式切换授权和产品能力变更

现行文档输入基线：`fe163b8`

实现核对基线：`b991f4c`

最后审查日期：2026-07-20

逐份 Owner 的事实归宿与判定见[现行事实迁移审计](fact-migration-audit.md)。本文件只保留切换时需要执行的结构映射，避免维护第二份事实清单。

## 1. 目标目录

```text
docs/
  README.md
  blueprints/
  design/
  implementation-map/
  conformance/
  engineering/
  temp/

guides/             # Report: user/developer
reports/            # Report: performance/governance/Gate/release/history
soma-examples/docs/ # registered Report: current executable scenarios
```

`docs-temp/reports/` 是 staging，不直接成为 `docs/reports/`。Root/module contributor reports保留既有路径；目录映射由 Engineering 文档治理登记。

## 2. 内容动作

| 当前内容 | 切换动作 |
|---|---|
| candidate Blueprint/Design/Map/Conformance/Engineering | promote 到 `docs/<category>/`，状态改为正式 |
| candidate user/developer Report | 与 `guides/` 合并或新增 current guide，不保留 staging duplicate |
| candidate performance/release Report | 与现行 current report/index核对；仅在有独立输出价值时转化为 dated/current Report |
| candidate governance Report | 转化为本次正式切换 Governance Report |
| 旧 root/module Design | 标记 superseded、声明唯一取代者并退出 current 入口；历史链接可保留 |
| `soma-examples/docs/` | 元数据改为 developer/current-executable Report，明确不拥有目标 Design |
| 旧 `docs/temp/*blueprint.md` | 修复引用后删除；目标内容由新 Blueprint拥有 |
| active documentation-system Temporary | 切换、验证和 Governance Report完成后的最后一步删除 |

## 3. 入口与 Agent 路由

- `docs/README.md` 成为正式分类入口；
- root/module README 不再创建 module Design island，只导航新 Design、Implementation Map、current executable Report和module code；
- `AGENTS.md` 的必读入口、Temporary规则、Gate/Report入口与新体系一致；
- `guides/README.md` 和 `reports/README.md` 分别拥有逻辑 Report 的 current 导航；
- current 导航不列 superseded Design或历史 Report。

## 4. 可执行治理

正式切换同时更新 `scripts/check-docs.sh`，至少覆盖 metadata、唯一入口、Design Owner、Implementation Map baseline、Report snapshot字段、superseded/current隔离、Temporary禁止引用与相对链接。`scripts/check.sh`继续把 docs Gate 作为全局前置检查。

Checker 只验证可执行结构规则；事实是否迁移完整仍以本专题的 Owner 审计和独立 review为准。

## 5. 边界

本迁移不修改 SOMA public/schema/runtime semantics，不处理 VRP/Simulation/Game implementation gaps，也不处理 G6 external release facts。上述差距已由 Conformance 分类，不阻塞文档体系切换。

切换必须是一个可回退变更；禁止把新入口、旧 Owner降级、checker和Temporary删除分散成长期中间状态。

# 候选文档体系切换检查表

类型：Temporary

状态：未满足，不执行切换

Owner：SOMA Java 文档体系重构专题

事实范围：未来原子切换的前置条件、执行边界、验证和回退要求

非事实范围：当前切换授权或执行计划日期

最后审查日期：2026-07-19

## 1. 前置条件

- 框架采用版本和项目偏差已明确；
- 全部旧正式 Owner 已完成事实级迁移复核；
- 每项长期事实在候选体系中只有一个 Owner；
- Blueprint、Design、Code/Tests 的已知差距已由 Owner 裁决；
- Implementation Map 已按 immutable candidate commit 重核；
- Reports 已区分当前、目标和差距；
- 当前 Temporary、长期研究蓝图和 user-owned tails 都有明确处置；
- 文档链接、metadata、Owner 和 prohibited-reference checks 可执行；
- 旧历史 reports/guides 的链接与保留策略已确定；
- 项目 Owner 已明确授权切换。

## 2. Rehearsal

在隔离 worktree 或等价可回退环境演练：

1. 把候选体系移动到正式目录布局；
2. 更新根/模块入口和 Agent 导航；
3. 更新文档检查与全局 Gate；
4. 处理旧文档和历史链接；
5. 确认不存在正式链接指向 Temporary；
6. 运行 docs check、`git diff --check` 和 `./scripts/check.sh`；
7. 由独立审查确认无事实遗失、双 Owner 或能力过度声明。

Rehearsal 结果进入 Governance Report，不改变当前正式体系。

## 3. 原子切换

获授权后，一个变更同时完成：

```text
promote candidate hierarchy
  + switch all official entrypoints
  + update executable checks
  + retire/supersede old owners without breaking history
  + publish conformance/governance result
  + delete documentation-system Temporary
```

禁止分批让旧 Design 与新 Design 长期并列为正式 Owner。

## 4. 验证与回退

切换 commit 必须在 clean state 通过与范围相称的完整 Gate。若发现事实遗失、链接断裂、检查失效或未裁决差距，整体回退候选切换，而不是让正式体系停留在中间状态。

## 5. 当前裁决

第一版候选内容已建立，但事实级迁移复核、脚本 rehearsal 和切换授权均未完成。本检查表当前明确禁止执行切换。

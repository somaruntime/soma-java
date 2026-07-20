# 文档体系重构专题

类型：Temporary

状态：切换准备完成，待正式授权

Owner：SOMA Java 文档体系重构专题

事实范围：候选体系的建设、迁移核对、成熟判定和未来切换方案

非事实范围：SOMA 正式设计、当前实现语义和 release 状态

专题基线：框架 `1.0.0-rc.2`，现行文档 `fe163b8`，实现 `b991f4c`

最后审查日期：2026-07-20

## 1. 意图

在不修改当前文档体系的前提下，于 `docs-temp/` 建设一套设计驱动的候选体系。先验证分类、Owner、颗粒度、导航和迁移完整性；成熟后再由项目 Owner 决定是否原子切换。

## 2. 当前授权范围

- 可以读取当前 Design、module docs、reports、guides、代码、测试和 scripts；
- 只在 `docs-temp/` 新建或修改候选文档；
- 不修改 `docs/`、各 module `docs/`、`guides/`、`reports/`、代码、测试、脚本或配置；
- 不实施 Conformance 中发现的代码/场景/release 差距；
- 不删除、移动或降级当前任何正式 Owner。

## 3. 专题产物

- [当前文档清单](current-document-inventory.md)
- [迁移映射](migration-map.md)
- [现行事实迁移审计](fact-migration-audit.md)
- [切换检查表](cutover-checklist.md)
- [正式切换前审查](pre-cutover-review.md)
- 候选 [Blueprint](../../blueprints/README.md)、[Design](../../design/README.md)、[Implementation Map](../../implementation-map/README.md)、[Conformance](../../conformance/README.md)、[Engineering](../../engineering/README.md) 和 [Reports](../../reports/README.md)

## 4. 当前状态

候选目录、rich Blueprint、Design Owner、executable contract boundary、Implementation Map、Conformance、Engineering、Report mapping、32份现行Owner迁移审计、目标路径 rehearsal和旧文档处置方案均已完成。本轮未发现候选体系的 blocking defect。

切换前的现行 checker与完整项目 Gate已经通过；尚未执行的是正式切换动作及其授权、新体系 checker落地、切换后完整 Gate和Temporary删除。因此候选体系保持未启用；本Temporary继续存在，不能提前删除。

## 5. 收口条件

专题只有在候选体系通过完整自审、迁移无事实遗失或双重 Owner、切换方案可回滚、相关检查通过并获得明确切换授权后才能收口。最后一步才是原子固化正式入口和删除本 Temporary；过程证据如需长期保留，先转化为 Governance Report。

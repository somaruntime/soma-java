# 文档体系重构专题

类型：Temporary

状态：进行中

Owner：SOMA Java 文档体系重构专题

事实范围：候选体系的建设、迁移核对、成熟判定和未来切换方案

非事实范围：SOMA 正式设计、当前实现语义和 release 状态

专题基线：框架 `1.0.0-rc.2`，项目 commit `4b6fa43`

最后审查日期：2026-07-19

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
- [切换检查表](cutover-checklist.md)
- 候选 [Blueprint](../../blueprints/README.md)、[Design](../../design/README.md)、[Implementation Map](../../implementation-map/README.md)、[Conformance](../../conformance/README.md)、[Engineering](../../engineering/README.md) 和 [Reports](../../reports/README.md)

## 4. 当前状态

已完成候选目录、入口、核心内容、第一版迁移映射、手工 metadata/link 检查和一次完整 `./scripts/check.sh` 非回归验证。当前仍未完成：逐条 lossless fact review、候选链接/metadata 的正式自动检查方案、切换 rehearsal、旧文档最终处置和明确切换授权。

因此本专题保持进行中，候选体系保持未启用；Temporary 不能删除。

## 5. 收口条件

专题只有在候选体系通过完整自审、迁移无事实遗失或双重 Owner、切换方案可回滚、相关检查通过并获得明确切换授权后才能收口。最后一步才是原子固化正式入口和删除本 Temporary；过程证据如需长期保留，先转化为 Governance Report。

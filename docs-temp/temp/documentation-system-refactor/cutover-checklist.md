# 候选文档体系切换检查表

类型：Temporary

状态：切换前条件已满足，等待授权与执行

Owner：SOMA Java 文档体系重构专题

事实范围：原子切换的已满足前置条件、执行顺序、最终验证和回退要求

非事实范围：授予切换权限、指定发布日期或改变产品/release状态

最后审查日期：2026-07-20

## 1. 切换前条件

- [x] 采用框架 `1.0.0-rc.2`、目录映射和项目 Owner已明确；
- [x] 32份旧正式 Owner已完成事实级迁移审计；
- [x] 长期语义、当前 executable surface、Engineering过程和Report输出各有唯一归宿；
- [x] 四份场景 Blueprint已保留丰富的使用者视角，不再是摘要占位；
- [x] Blueprint/Design/Code/Tests已知差距均已分类并有 Owner处置；
- [x] Implementation Map以 immutable implementation commit `b991f4c`核对，后续到`fe163b8`无实现树变化；
- [x] Guides、Reports、module executable example与历史输出的物理映射已确定；
- [x] 旧 Owner、旧 Blueprint、candidate Reports和active Temporary均有切换动作；
- [x] metadata/link/index/baseline/prohibited-temp checks已在目标路径 rehearsal；
- [ ] 项目 Owner明确授权正式切换。

未完成项只有授权和切换动作本身；G6 release blocked及已裁决场景目标差距不属于本文档切换的前置缺陷。

## 2. 原子执行顺序

获授权后，在一个可回退变更中按顺序完成：

1. 锁定 clean cutover candidate，确认 implementation tree仍与`b991f4c`一致或更新 Map baseline；
2. promote `docs-temp/{blueprints,design,implementation-map,conformance,engineering}` 到正式 `docs/` 分类；
3. 重写 `docs/README.md`、root/module README、`AGENTS.md`、`guides/README.md` 和 `reports/README.md`；
4. 将旧 root/module Design标记superseded并链接唯一取代者；把`soma-examples/docs` reclassify为developer/current-executable Report；
5. merge/转化/删除 `docs-temp/reports` staging，修复旧 Blueprint与历史链接；
6. 更新 `scripts/check-docs.sh`，执行新体系 metadata/Owner/current/superseded/temp/report/map规则，并用分类化信息预算取代统一500行限制；
7. 更新 Conformance与正式 Governance Report，运行全部验证；
8. 最后删除 documentation-system Temporary和空的`docs-temp/`，确认没有正式引用指向它。

禁止在第2至第8步之间形成跨 commit的长期中间状态。

## 3. 最终验证

- 新 `./scripts/check-docs.sh`；
- `git diff --check`；
- 32份旧 Owner处置覆盖检查；
- current入口、Design Owner与superseded隔离审查；
- 全仓相对链接与Temporary引用检查；
- `./scripts/check.sh`；
- clean worktree上的独立终审。

Governance Report必须记录cutover commit、实际命令、结果、旧Owner处置、已知产品差距和`V1 scope non-regression`。文档切换不改变public/schema/runtime capability或G0–G6状态。

## 4. 回退

发现事实无归宿、双重 Owner、current/superseded混淆、断链、checker漏检、Gate失败或Temporary残留时，整体回退切换。不得用保留旧新两套current入口作为“临时修复”。

## 5. 当前裁决

候选内容和切换方案已经具备执行条件，但尚未获得本次正式切换授权，也未修改任何现行Owner、入口或脚本。因此当前正确状态是`ready for cutover, not enabled`。

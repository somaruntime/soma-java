# SOMA Java Engineering

类型：Engineering Entry

状态：Implementation Ready / Not Started

正式事实源：是（仅拥有实施过程与工作分解，不拥有产品语义）

Owner：SOMA Java production implementation 路由、slice 状态、实施 stop rule 与 evidence 回链

最后审查日期：2026-08-01

## 1. 文档责任

本目录回答“按什么顺序实现、每一步怎样证明完成”。

- [Blueprint](../blueprint/README.md)拥有产品目标；
- [Design](../design/README.md)拥有规范性合同；
- 本目录拥有实施顺序、入口/出口、变更控制和当前进度；
- [Conformance](../conformance/README.md)拥有实际证据与差距；
- production source 出现后，code/module README 只解释本地实现，不改写上游语义。

## 2. 当前状态

截至 2026-08-01：实施准备已完成，production implementation 尚未开始。仓库中没有
`pom.xml`、production module/source/test、可用 API、artifact、benchmark、Example、CI
或 release workflow。

开始 I0 仍需要 Product Owner 对“进入实施”这一动作单独授权。`READY_FOR_IMPLEMENTATION`
表示可以按已关闭的 Design 开始，不表示实现或产品已经 ready。

## 3. 唯一当前计划

[V1 实施计划](v1-implementation-plan.md)是当前唯一 production implementation plan。
不得另外建立 phase/roadmap/checklist 复制同一状态；具体 slice 的临时调查材料只能存在
于 active Temporary，结论晋升后立即退役。

## 4. 实施纪律

1. 同时只允许一个 slice 为 `IN_PROGRESS`；
2. 每个 slice 从一个 independent consumer-visible journey 向下贯通，不建立长期假实现；
3. 先窄后宽验证；unchanged-input evidence 不无理由重复；
4. production surface 先做 admission，再创建文件/type/dependency/workflow；
5. implementation 发现 Design 不能成立时停止该 slice，记录最小反例，不在代码中发明
   新语义；
6. predecessor 只提供问题/历史 evidence，不复制 source、module、test 或 API；
7. 每次完成必须同时更新 slice status 与 Conformance evidence，不用提交信息代替证据；
8. correctness、failure、resource 和 determinism 成立后才能以 profile 驱动优化。

## 5. Implementation Map 规则

当前不创建假想 package/class 级 Implementation Map。某个 production component 真正
出现并稳定后，才可以在对应 module 的 engineering map 中记录：

- capability 与 Design Owner；
- code Owner/path；
- lifecycle/failure boundary；
- conformance test/evidence；
- current known gap。

Map 是已实现事实的索引，不是预建 architecture hierarchy。

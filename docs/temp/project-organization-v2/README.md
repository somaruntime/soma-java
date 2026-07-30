# SOMA Java 项目组织框架 2.0 迁移

类型：Temporary

状态：active

Owner：SOMA Java 项目组织治理

事实范围：本次目录、入口、角色投影、交付 manifest 与 checker 的迁移范围和切换条件

非事实范围：SOMA 产品语义、public/generated API、runtime 行为、性能基线和 release 结果

最后审查日期：2026-07-30

## 1. 授权与目标

Product Owner 已批准按照《面向角色与场景的项目组织框架》`2.0.0-rc.1`
完成本次治理，并授权自主规划、实施、验证、提交和推送。

本专题只改变项目信息架构和交付选择：

- 将完整项目事实集中到 `project/`；
- 将根 `README.md` 与 `docs/` 重构为角色和场景投影；
- 将模块与参考应用内部文档集中到 `project/modules/`；
- 建立精简源码交付 allowlist；
- 同步更新入口、相对链接、Agent Skill、checker 和 release evidence；
- 在迁移后的同一 clean SHA 上重新执行 release qualification。

## 2. 非目标与不变量

- 不修改 Blueprint、Design 或 V1 产品边界；
- 不修改 Java production/public/generated/schema/runtime semantics；
- 不修改测试、benchmark workload、baseline 或 Gate 成功标准；
- 不把 private-source readiness 外推为 public、Maven Central 或 production
  readiness；
- 不保留新旧两套 current 路径、迁移 checker、tombstone 或 archive；
- 不把 `project/` 当成访问控制边界，也不从 Git 中移除正式项目事实。

发现内容冲突时，先由现有唯一 Owner 与 Conformance 裁决，不能用目录迁移顺手改写。

## 3. 当前事实分类与目标路径

| 当前入口 | 分类 | 目标入口 |
|---|---|---|
| `docs/blueprints/` | PROJECT / Blueprint | `project/blueprint/` |
| `docs/design/` | PROJECT / Design | `project/design/` |
| `docs/implementation-map/` | PROJECT / Implementation Map | `project/implementation-map/` |
| `docs/conformance/` | PROJECT / Conformance | `project/conformance/` |
| `docs/engineering/` | PROJECT / Process | `project/process/` |
| `reports/` | PROJECT / Report | `project/reports/` |
| `guides/java-v1-install-and-consumer-guide.md` | PRODUCT | `docs/getting-started/` |
| V1 白皮书与应用开发者手册 | PRODUCT | `docs/architecture/`、`docs/guides/` |
| `guides/development-guide.md` | PROJECT / Process | `project/process/` |
| `soma-*/docs/` | PROJECT / Modules | `project/modules/<module>/` |
| 三个参考应用的 Blueprint/Design/Validation | PROJECT / Modules | `project/modules/soma-examples/<application>/` |
| 三个参考应用的运行入口 | PRODUCT | application 根 `README.md` 与 `docs/examples/` |

根级 `AGENTS.md`、`CONTRIBUTING.md`、`SECURITY.md`、`SUPPORT.md` 和
`CHANGELOG.md` 因工具、社区和平台发现要求保留，但只作为入口。

## 4. 切换规则

迁移采用一次性 cutover：

1. 建立 `project/README.md` 和目标分类；
2. 通过移动保留全部正式事实与 Git history；
3. 同步生成面向使用者和技术选型者的 `docs/` 投影；
4. 同步更新所有链接、脚本、Skill 与治理规则；
5. 删除旧目录和旧入口；
6. 完成角色旅程、文档、scope、package 与 Full Gate；
7. 将稳定迁移事实写入 `project/process/documentation-governance.md` 和正式 Report；
8. 删除本 Temporary。

在 cutover 完成前，当前 `docs/`、`guides/` 和 `reports/` 仍是正式入口；
本文件不拥有迁移后的长期事实。

## 5. 交付边界

Maven 发布物继续只包含 parent/module POM、四个 production module 的 binary、
sources、Javadoc JAR 及必要 LICENSE/NOTICE。

新增的精简源码包由 allowlist 生成并验证，包含构建入口、production modules、
必要用户文档、示例、license 和 provenance；默认不包含 `project/`、内部报告、
benchmark、tests、原始 evidence、本机文件或 secret。托管平台提供的 repository
snapshot 仍视为完整仓库快照，不冒充精简源码包。

## 6. 验收与退役

必须同时满足：

- 使用者不进入 `project/` 即可完成获取、安装、最小运行、排障和升级；
- 技术选型者可获得架构、性能适用条件、限制与证据链接；
- 贡献者可从 `project/modules/` 找到 Design、实现和验证；
- 维护者可从 `project/README.md` 到达全部正式事实；
- 旧 current 路径、平行 Owner、失效链接、未登记文档和迁移专用规则为零；
- 精简源码包的内容集合、checksum 和 provenance 可验证；
- 文档、scope、Full、package/security 与 private qualification 在最终同一 SHA
  上成功；
- production/public/test/fixture/benchmark surface 无语义变化。

完成后删除整个 `docs/temp/project-organization-v2/`。

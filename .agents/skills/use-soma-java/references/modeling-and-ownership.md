# 建模、Identity 与 Ownership

适用 SOMA 版本：`1.0.x`

正式 Owner：

- [产品 Blueprint](../../../../docs/blueprints/soma-java-product-blueprint.md)
- [Schema 与生成 API](../../../../docs/design/schema-and-generated-api.md)
- [Ownership 与 lifecycle](../../../../docs/design/ownership-and-lifecycle.md)

本参考只给 AI consumer 决策顺序，不重新定义上述语义。

## 建模顺序

先以 application 语言识别事实，再选择 SOMA shape：

| 问题 | 决策 |
|---|---|
| 事实角色是什么？ | input、working、frontier/workspace、result；角色不直接决定 table kind |
| row 有稳定业务 identity 吗？ | 有则 keyed；没有则 dense |
| lifecycle 由谁拥有？ | 独立 aggregate 使用 root；严格属于一个 parent row 的组成部分使用 child |
| equality access 是否稳定且高频？ | primary identity 用 Key；secondary one-to-one 用 Unique；secondary one-to-many 用 Index |
| 多个 root 是否共同释放和受同一资源包络？ | 只有答案为是才显式 Group |

Key/Unique/Index 都是语义访问声明，不承诺 B+Tree、长期排序或 range query。
物理 Index 和遍历顺序不是业务 identity。业务 tie-break 与跨 operation 顺序继续由
application 的稳定 key/comparator/专用结构拥有。

## 可进入 live storage 的 shape

V1 只接受：

- primitive-backed scalar；
- 正式白名单中的 immutable `String` reference；
- compiler-flattened `@SomaValue`；
- parent-owned child。

普通 object、array、DTO、`List`、`Map` 或任意 object graph 不得成为 live
field。`@SomaTable` carrier 是 Schema 和 detached materialization shape，不是可
长期持有的 live entity。Batch 是写入 staging，不是第二份 live fact。

## Root、Child 与 Group

- root ownership aggregate 有单一同步 Owner；
- child 只能属于一个 parent row，不 share、不 reparent，不绕过 parent release；
- Group 只拥有 composition、resource 与 lifecycle，不提供跨 Table transaction；
- read-only multi-source DataFlow 可以显式 bind 多个 root，不因多 source 自动要求
  Group；
- application 继续拥有业务提交、回滚、I/O、恢复和并发隔离。

## 建模自审

实现前应能明确回答：

- stable identity 的字段和相等语义是什么；
- dense row 在 compaction 后为何不依赖旧 Index；
- child 的 parent、创建、替换和释放路径是什么；
- exact access 是 primary、secondary unique 还是 non-unique；
- 哪些数据只应在 boundary materialize；
- 失败后哪一个 Owner 仍持有可信事实。

任何答案依赖“以后再加 migration”、共享 object graph 或隐式全表重建时，先停止
实现并重新建模。

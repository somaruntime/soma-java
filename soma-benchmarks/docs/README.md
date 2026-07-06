# soma-benchmarks 正式设计文档

本目录保存 `soma-benchmarks` 的正式设计文档。

## 当前正式设计文档

当前尚无正式契约文档。

## 待决策设计缺口

本次治理只确认缺口，不替代设计：

- benchmark runner 的输入参数、输出 JSONL schema、环境信息字段和失败语义尚未形成正式契约；
- benchmark smoke 与性能 claim 的分界已有根级门禁约束，但模块级场景目录、规模、重复次数和报告字段尚未形成正式契约；
- Row Pipeline、ColumnView / primitive loop、DTO materialization / Java Stream 三类路径的对比证据口径尚未形成正式契约。

这些缺口会影响 G5 benchmark smoke 和未来性能声明，但不阻塞当前文档归位治理。

## 临时设计目录

临时设计草案只能放在 `docs/temp/`。草案被接受后，必须把稳定事实迁移进本目录正式设计文档。

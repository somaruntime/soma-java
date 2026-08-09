# Changelog

本文件记录 SOMA 对使用者可见的产品变化。当前 checkout 尚未形成已发布版本；远端 Release、
Package、签名与正式发布声明仍需 Product Owner 单独授权。

## Unreleased — V1 implementation candidate

- 建立 Java 8 `soma-runtime` 与 `soma-processor` 两项 production artifact；
- 建立 package-level schema、full-regeneration 与 generated typed Table API；
- 实现 chunked Table storage、Key/Index、point/selection mutation 与 structured failure；
- 实现 typed query IR、reference/optimized execution、GroupBy、Equality/Cross Join；
- 实现 bounded explicit parallel execution、AUTO/OFF compression 与四级 metadata；
- 增加调度、仿真和实时派工三个 reference application；
- 增加百万行 profile、local package qualification 与 non-publishing CI workflow。

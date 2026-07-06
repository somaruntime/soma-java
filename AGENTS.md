# soma_java Agent Guide

`soma_java` 是 SOMA Java-only 方向的原型仓库，用于探索 Java annotation schema + Java columnar runtime。

## Project Boundary

本仓库当前只承载 Java 8 使用场景，不承诺 Python、C ABI、native runtime 或跨语言 FFI。

当前设计方向：

```text
Java annotation schema
  -> Java 8 annotation processor
  -> normalized schema model / schema hash
  -> generated Table / Batch / Record / View / ColumnView
  -> Java columnar runtime kernel
  -> examples / benchmark / gate evidence
```

## Module Ownership

- `soma-annotations` owns public schema annotation API.
- `soma-processor` owns annotation processing, validation, normalized schema model, schema hash, and code generation.
- `soma-runtime-core` owns Java columnar runtime kernel, primitive columns, bitmap, sparse set, indexes, order sidecar, lifecycle, and runtime errors.
- `soma-testkit` owns compile/golden/runtime invariant test helpers.
- `soma-examples` owns Java 8 usage examples and end-to-end smoke scenarios.
- `soma-benchmarks` owns benchmark scenarios and evidence collection.

## Design Rules

- Java annotation schema is schema source only; it must not become runtime row storage.
- Generated runtime must be table-first and columnar, not `List<DTO>` hot-loop storage.
- Public/generated APIs must not expose runtime sidecars, hash buckets, bitmap words, allocator policy, or internal row pointers.
- No third-party dependencies should be added before an explicit design decision.
- Keep Java 8 compatibility unless a formal design document changes the baseline.

## Documentation Governance

文档、报告和代码注释默认使用中文。API 名称、类型名、包名、Maven 坐标、文件路径、命令和机器可读文本可以保留英文。

Formal design facts live under `docs/`. Temporary drafts live under `docs/temp/`. Formal reports live under `reports/`. Temporary reports live under `reports/temp/`.

## Git Governance

长期分支只使用 `main`、`develop`、`release`。常规设计和实现工作在 `develop`。不要创建 `codex/`、`feature/`、`bugfix/` 或其他临时长期分支。

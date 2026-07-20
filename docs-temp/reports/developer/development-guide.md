# SOMA Java 开发与维护指南

类型：Report / 开发者文档

状态：候选

Owner：SOMA Java 开发者输出

受众：修改 SOMA compiler、generated API、runtime、examples 或 benchmark 的维护者

事实范围：当前项目的事实定位、变更路由、验证入口和维护约束

适用版本：commit `b991f4c`

输入事实源：候选文档体系、当前代码、scripts 和 Gate reports

最后审查日期：2026-07-20

## 1. 先定位事实

在候选体系中，先从 [Blueprint](../../blueprints/README.md) 确认目标，从 [Design](../../design/README.md) 找规范性 Owner，再用 [Implementation Map](../../implementation-map/README.md) 进入代码。发现偏差时记录 [Conformance](../../conformance/README.md)，不要让当前代码反向成为设计理由。

本体系尚未正式启用；真正修改当前项目时，仍须先从当前 [`docs/README.md`](../../../docs/README.md) 读取正式 Owner。

## 2. 变更路由

| 变更 | 主要入口 | 至少验证 |
|---|---|---|
| annotation/schema | annotations + processor | compile fixtures、schema/golden、external consumer |
| generated public API | generator | public manifest/`javap`、external compile/run、runtime binding |
| runtime protocol/storage | runtime-core + generator | kernel invariant、generated dense/keyed/access/child tests |
| lifecycle/error/materialization | runtime-core + generator | failure path、atomicity、external child/breadth consumers |
| scenario | examples schema + scenario | executable output、relevant benchmark lane |
| benchmark/evidence | benchmarks + validator/scripts | smoke、negative artifact、checksum/claim rules |
| docs only | corresponding Owner | docs links/metadata、`git diff --check` |

如果修改长期规范性设计，先在 Temporary 独立完成候选设计和授权；普通内部重构无需制造专题仪式。

## 3. 常用命令

```text
./mvnw -B -ntp verify
./scripts/check-docs.sh
./scripts/check.sh
./scripts/check-benchmark-smoke.sh
./scripts/package-smoke.sh
```

完整验证使用 full JDK 8。窄检查适合反馈，跨模块/public/generated/protocol 收口运行 `./scripts/check.sh`。

## 4. 代码约束

- 保持 Java 8；
- 不在 runtime hot storage/path 引入反射、Stream、DTO/object graph、boxed tuple 或 metadata interpreter；
- 不把 generated-runtime protocol 暴露成 application SPI；
- 不用 test-only bypass 代替 production semantics；
- 不新增第三方 runtime dependency，除非 Design 已批准；
- 不恢复 Sparse Set、dirty selector rebuild、maintained physical order 或 stable raw Index；
- 保留 mutation atomicity、full equality、ownership 和 structured failure。

## 5. 收口

确认变更仍服务 Blueprint，相关 Design/代码/测试一致，Implementation Map 已按最终 commit 核对，Report 没有过度声明。若使用 Temporary，最后原子固化长期事实并删除 Temporary；有未裁决差距时不能宣布专题完成。

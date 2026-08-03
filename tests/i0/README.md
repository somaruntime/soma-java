# I0 Qualification Fixtures

本目录只服务于 I0 build/full-regeneration 资格证明，不是第三个 production module 或
published artifact。

测试路由：

| Taxonomy | Owner |
|---|---|
| unit / failed-state | `soma-runtime` 的 `RuntimeConfigurationProbe` |
| golden / compile-negative | `soma-processor` 的 `I0ProcessorHarness` |
| independent consumer | `consumer/`，runtime classpath 与 processorpath 分离 |
| integration / stale cleanup | `scripts/check-i0.sh` 对 consumer 副本执行完整生成、删除和重命名 |
| packaged runtime mismatch | `runtime-swap/`，只在 test classpath 注入不兼容 runtime stand-in |

正式入口只有仓库根目录的：

```bash
./scripts/check-i0.sh
```

正式结果见[I0 Build Spine Qualification](../../project/conformance/i0-build-spine-qualification.md)。

脚本要求完整 JDK 8、standard Maven 与公共 Maven Central。默认复用当前 Java user home 下的
Maven local cache，避免把重复下载 build-plugin graph 误当成产品正确性 Gate；若要单独验证空
cache 的 repository provenance，可用 `SOMA_I0_MAVEN_REPOSITORY` 指向新目录。脚本运行后删除
其临时工作目录；generated source、class、artifact 和报告都留在 ignored build output 中，不
提交为产品事实。

I0 的 wrapper policy 是不提交 Maven Wrapper binary/JAR：qualified build 记录 Maven 版本，
standard Maven reactor 是唯一 build owner。未来若 CI/release qualification 需要 wrapper，必须在
对应 Gate 中把 wrapper distribution、checksum 和更新 Owner 一并准入。

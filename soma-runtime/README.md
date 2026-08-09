# soma-runtime

`soma-runtime` 是 SOMA 的 application-facing production artifact，包含 schema annotations、
shared API、generated code linkage 与执行运行时。普通使用者不应直接依赖
`io.github.somaruntime.soma.internal`；Table、Field、Index、View、Editor 与 pipeline surface
由 `soma-processor` 根据 schema 生成。

当前 artifact 坐标为：

```xml
<dependency>
  <groupId>io.github.somaruntime.soma</groupId>
  <artifactId>soma-runtime</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

仓库尚未发布远端 artifact。请先按[根 README](../README.md#快速开始)从源码安装，再构建下游
application。完整使用路径见 [`soma-examples`](../soma-examples/README.md)。

# soma-processor

`soma-processor` 是 SOMA 的 Java 8 aggregating annotation processor。它收集一个
`@SomaSchema` package 直接包含的完整 `@SomaTable` source set，验证 schema、类型、命名和
capability，并生成唯一的 `Soma`、`SomaGroup` 与 typed Table API。

它是编译期 production artifact，不是 application runtime 的通用 reflection layer。标准构建应
将 `soma-runtime` 放在 classpath，将 `soma-processor` 与 `soma-runtime` 放在 processorpath，
并向 processor 提供完整 source set。

当前 artifact 坐标为：

```xml
<dependency>
  <groupId>io.github.somaruntime.soma</groupId>
  <artifactId>soma-processor</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

仓库尚未发布远端 artifact。构建方式和可运行 schema 见[根 README](../README.md)与
[`soma-examples`](../soma-examples/README.md)。

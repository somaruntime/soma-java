# Executable qualification fixtures

本目录保存跨 artifact、annotation processor、generated source 与独立 Java 8 consumer 边界的
可执行证据。它们按长期能力命名，而不是按已经完成的 I0–I7 实施阶段命名：

- `build-spine/`：artifact、linkage 与 full-regeneration；
- `group-relation/`、`parallel-execution/`、`compression-metadata/`：当前 cumulative Java 8 consumer；
- `regeneration/build-spine/`：仍由当前 artifact qualification 使用的 stale-source fixture。

模块内 `src/test` 负责实现单元和不变量测试；本目录负责真实 processorpath/classpath、generated
ABI、compile-negative 与 downstream consumer 证明。Primitive/schema/query/mutation 的当前实现
与不变量由 module tests 和 cumulative consumer 承接。I2–I4 point-in-time golden 已移入
`project/engineering/history/`；I1 slice-only fixture 因依赖旧 internal constructor 直接退役。

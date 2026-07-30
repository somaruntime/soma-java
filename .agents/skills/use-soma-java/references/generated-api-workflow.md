# Maven、Schema 与 Generated API 工作流

适用 SOMA 版本：`1.0.x`

正式 Owner：

以下路径相对于安装来源固定的 SOMA source ref：

- `docs/getting-started/java-v1-install-and-consumer-guide.md`
- `project/design/schema-and-generated-api.md`
- `project/implementation-map/compiler-and-codegen-map.md`
- `project/implementation-map/test-and-evidence-map.md`

精确坐标、plugin 参数和方法名必须从 consumer 所固定版本的 POM、指南与生成结果
读取；本参考不保存第二份完整 API。

## 兼容性锚点

本 Skill 的 `1.0.x` 行为校准到 `soma-generated-runtime-v12`、
`soma-runtime-java8-v12`、`soma-runtime-plan-v6`、
`soma-transformation-v5` 与 `soma-kernel-v6`。这些 token 只用于发现
generated/runtime/compiler 代际漂移，不授权 consumer 直接构造或覆盖 protocol。
任一 identity 变化时必须重新审查本 Skill、external consumer 和行为 eval。

## 工具链与 artifact

使用该版本声明的 Amazon Corretto full JDK 8 和 repository Maven Wrapper。典型
consumer 需要：

- `soma-annotations`：Schema annotation；
- `soma-runtime-core`：Table、Plan、lifecycle 与 runtime；
- `soma-dataflow`：使用 typed Transformation/DataFlow 时的 runtime；
- `soma-processor`：编译期 processor/plugin，只进入 build path。

不要让 `soma-processor` 成为 application runtime dependency，也不要为“兼容”
增加未批准的第三方 runtime dependency。

## 生成优先流程

1. 固定 SOMA version/ref，不使用浮动分支或未核验 snapshot；
2. 配置 Maven compiler 的 Java 8 source/target、Soma javac plugin 与 annotation
   processor path；
3. 在 package 中声明 `@SomaSchema` 和 generated package；
4. 声明最小 `@SomaValue` / `@SomaTable` shape 与真实 Key/Unique/Index/child；
5. 运行 clean compile；
6. 检查 processor diagnostics；
7. 从 `target/generated-sources/annotations`、generated class `javap` 或同版本
   external consumer fixture读取精确 surface；
8. 只使用实际存在的类型、方法、参数顺序和 lifecycle。

如果 compile 失败，先处理 schema/diagnostic；不要手写伪 generated class 让后续
代码“暂时能编译”。

## 精确 surface 的取证顺序

优先级如下：

1. 当前 consumer 的 generated source；
2. 当前 consumer 的 generated class 与 `javap`；
3. 同一 SOMA ref 的 golden/external consumer；
4. 当前版本 Consumer Guide 的已验证示例；
5. Design 只决定语义，不作为精确 signature 清单。

AI 不得根据 `XTable`、`XBatch` 或 `XDataFlow` 的命名规律猜测 `find`、`scan`、
`mutate`、`release` 等方法。生成结果与预期不符时，报告实际 diagnostic 和 source
位置，再修 Schema 或调用。

## Consumer 完成证据

最小验证必须在独立 Maven consumer 中完成：

- clean generate/compile；
- Java classfile major 52；
- 写入 Batch/Table；
- 执行所选 Point/Candidate/Column/Key/Bulk/Ownership 或 DataFlow journey；
- mutation 后读取到正确结果；
- 正常 release；
- runtime dependency tree 不包含 processor 或意外第三方 dependency。

记录 SOMA ref/version、Corretto/Maven/OS/architecture 和实际命令。只复制 POM、
只读取 generated 文件或只运行 SOMA reactor 内测试都不够。

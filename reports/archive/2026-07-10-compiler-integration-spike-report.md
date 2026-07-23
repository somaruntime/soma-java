# 2026-07-10 Compiler integration feasibility report

归档说明：本报告是特定时间点的历史 checkpoint，保留原结论与术语作为 provenance。

日期：2026-07-10
目标：验证 `@SomaValue` implicit immutable effective type 是否可以由 Java 8 build-time tooling 实现
输入事实源：annotation schema、schema processing、code generation、public compatibility 与 compiler integration contracts
范围：最小 class/field/constructor lowering；不实现完整 processor/runtime

## 1. Environment

- OS：macOS arm64；
- Java 8：Zulu OpenJDK `1.8.0_492`；
- comparison compiler：Homebrew OpenJDK `25.0.2`；
- Maven：`3.9.16`；
- Maven Compiler Plugin：`3.11.0`。

Spike source 位于临时目录，治理结论固化后删除；本报告保留命令形状和可支持 claim，不把 spike code 当作 production implementation。

## 2. Cases

### 2.1 Standard Filer replacement

普通 JSR 269 processor 对 initial input `spike.MachineId` 调用 `createSourceFile("spike.MachineId")`，随后生成同名 class。

结果：编译失败，diagnostic 为 `duplicate class: spike.MachineId`。

结论：Filer/code generation 不能替换现有 schema class，不能单独实现“schema declaration 本身就是 materialized effective type”。

### 2.2 Processor-round AST mutation

Processor round 内修改 class/field flags 并向 tree 添加 constructor。

结果：编译失败，javac 仍按已经进入 symbol table 的 default constructor/field state执行 flow，报告 final field 未初始化。

结论：只在 ordinary annotation-processing phase 改 tree 太晚；需要在 symbol enter 之前 lowering，或实现更深的 symbol/member injection。V1 选择更清晰的 parse-phase plugin boundary。

### 2.3 javac 8 parse-phase plugin

`com.sun.source.util.Plugin` 在 `TaskEvent.Kind.PARSE` 完成后、Enter 前：

- 将 `MachineId` 设为 final；
- 将 `value` 设为 public final；
- 注入 `MachineId(long)`；
- 同一 javac invocation 编译调用方 `new MachineId(7L)`。

结果：编译成功。

Classfile inspection：

```text
final class spike.MachineId {
  public final long value;
  public spike.MachineId(long);
}
```

独立编译 `machineId.value = 9L` 失败，diagnostic 为 `cannot assign a value to final variable value`。

### 2.4 Maven consumer path

临时 Maven consumer 使用 Java 8、Maven Compiler Plugin `3.11.0`、build-only transformer JAR 和 `-Xplugin:SomaValue` 完成 `clean compile`。

结果：成功；Maven 产出的 `MachineId.class` 与 direct javac inspection 一致。

结论：javac plugin 可以进入 Maven consumer build；最终 artifact coordinates、plugin name 和 processor path 仍须由真实 `soma-processor` external-consumer fixture 固化。

### 2.5 JDK 25 negative comparison

把同一 javac 8 adapter 直接交给 OpenJDK 25 `javac --release 8`。

结果：失败，`jdk.compiler` module 未向 unnamed module export `com.sun.tools.javac.api`，出现 `IllegalAccessError`。

结论：`--release 8` 不会让 javac internal API 变成 javac 8；javac 8 adapter 不能自动宣称支持 JDK 9+。每个新 javac family 需要独立 adapter、module flags/design review 和 gate evidence。

## 3. Accepted conclusion

可支持的结论：

- `@SomaValue` implicit final/public-final/canonical-constructor 语义在 javac 8 parse-phase plugin 中技术可行；
- 同一 javac invocation 的用户源码和 classfile 可以看到同一 effective type；
- Maven consumer 可以显式激活该 plugin；
- standard JSR 269 processor 单独不足以替换 initial input type；
- V1 必须使用 transformer + processor，并在 transformer 缺失/unsupported compiler 时 fail closed。

本报告不支持的结论：

- 完整 equality/hash/toString lowering 已实现；
- processor element model/codegen 全链路已完成；
- incremental build 已正确；
- IntelliJ editor/JPS/ECJ 已支持；
- JDK 17/21/25 compiler 已支持；
- G2/G4 已通过。

## 4. Governance impact

- 新增 `soma-processor/docs/compiler-integration-contract.md`；
- build contract 固定 transformer + processor consumer model；
- compatibility identity 增加 compiler/lowering identity；
- G2/G4 增加 transformer、unsupported compiler 和 external consumer evidence；
- 不新增空 compiler-adapter module；第二个真实 adapter 出现后再复审拆分。

## 5. Reproduction command shapes

```text
javac -cp <plugin> -Xplugin:SomaValue -d <out> SomaValue.java MachineId.java Consumer.java
javap -classpath <out> -p spike.MachineId
mvn -f <temporary-consumer-pom> clean compile
javac --release 8 -cp <javac8-plugin> -Xplugin:SomaValue ...   # expected unsupported failure on JDK 25
```

临时 source/artifact 路径不保留为 repository contract；正式复现入口必须由 future processor/testkit fixture 提供。

## 6. Claim boundary

本次 spike 关闭的是 implementation feasibility decision，不是 feature completion。SOMA Java 仍处于 pre-implementation 状态，G1-G6 保持未通过。

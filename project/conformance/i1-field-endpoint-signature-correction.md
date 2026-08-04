# I1 Field Endpoint Signature Correction

类型：Conformance / M2 Targeted Design Correction

状态：`PASS`

正式事实源：是（`SomaFieldEndpoint`命名裁决、targeted evidence与replacement closure）

Owner：I1实施前Field annotation/endpoint同名冲突的正式处置

裁决日期：2026-08-04

## 1. Counterexample

原Signature同时要求根package存在：

```java
public @interface SomaField {}
public interface SomaField<R, V> {}
```

二者占用同一FQN `io.github.somaruntime.soma.SomaField`。Qualified Amazon Corretto
`javac 1.8.0_502`稳定拒绝为`duplicate class`；annotation declaration grammar也不能通过添加
generic parameter兼任marker。I0已实现annotation，I1首次需要typed endpoint，因此这是最早充分
implementation counterexample。

## 2. Product Owner裁决

Product Owner于2026-08-04批准：

> 保留`@SomaField`，将generic marker正式改名为`SomaFieldEndpoint<R,V>`，不保留旧名或兼容
> 别名，并允许最小修正Signature Design。

正式shape为：

```java
public @interface SomaField {}

public interface SomaFieldEndpoint<R, V> {}

public interface SomaKeyableField<R, V>
        extends SomaFieldEndpoint<R, V> {}
```

所有原`SomaField<L,V>` marker位置机械改为`SomaFieldEndpoint<L,V>`。Field identity、owner
provenance、Keyability、Group/Join type narrowing、operation/result/null/failure与runtime语义均不
改变。

## 3. Targeted evidence

- 同FQN annotation/interface反例在Java 8失败为`duplicate class: proof.SomaField`；
- 推荐三个type组合在同一Java 8编译器通过；
- 独立`compiler_audit`确认不存在不改变public signature即可满足原两个声明的合法实现；
- `SomaFieldEndpoint`是保留常用annotation、避免双API且与“typed Field endpoint”叙事一致的最小
  修正；
- 全仓current Design搜索不再把generic marker写作`SomaField<R,V>`；历史readiness记录仅保留
  provenance，不能覆盖current Signature Owner。

## 4. Scope 与 claim boundary

本修正只关闭一个不可编译的public type name，不提前实现I1 generated surface，也不证明G2
I1 scope已经通过。I0 executable annotation与artifact不变，不需要重跑I0全量qualification；I1
生成、`javap`、consumer positive/negative仍必须按原Gate完成。

独立`compiler_audit`已对正式修正逐项复核并判定`PASS`：修正完整、机械且无语义漂移。
原bounded Temporary已经删除，replacement closure完成；不保留旧generic marker、alias或
parallel Design。

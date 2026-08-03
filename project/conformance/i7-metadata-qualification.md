# I7 Metadata/Plain Representation Qualification

状态：I7_SCOPE_PASS（metadata 与 PLAIN representation baseline）；不是完整 I7/G8。

覆盖：

- 四个 immutable metadata carrier 使用显式 private constructor；
- Soma._metadata() 观察 configuration state、effective budget 与 AUTO/OFF policy；
- SomaGroup._metadata() 观察 default identity；
- Table._metadata() 观察 logical name、size、capacity、state version、plain payload/
  representation estimate；
- Field _metadata() 观察 logical path/type/nullability/key/index role；
- metadata 不创建 default Group、不替代 runtime operation guard；
- `UNFROZEN / -1 / null` sentinel 不冻结配置，配置随后仍可成功发布；
- Table metadata 是 detached snapshot，跨 add/remove 的 StateRoot publication 不回写旧 carrier；
- Java 8 generated source 与 I1-I6 regression。

未覆盖且不作声明：

- BIT_PACKED/FRAME_OF_REFERENCE/DELTA/RLE/DICTIONARY codec；
- encoded kernel、sparse overlay、rebuild、compression-aware resource admission；
- complete _explain() plan text、global managed-memory accounting、I8 scenarios/performance；
- 完整 G8。

正式 Blueprint/Design 未修改。

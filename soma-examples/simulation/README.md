# Simulation reference application

`Event` Table 不拥有隐式业务顺序。application 每一步都按 `eventMinute → priority → eventId`
显式排序，先取得 detached Event，再更新 EntityState，最后删除已消费 Event。两个 Table 的发布
是两个独立原子操作；若后一步异常，补偿协议由 application 拥有。

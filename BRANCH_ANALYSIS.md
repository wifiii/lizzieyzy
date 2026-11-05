# 棋谱分支补充分析

在「分析 → 棋谱分支补充分析…」中启用此功能，并设置每个分支选点的最低
visits（默认 1000）。加载棋谱、停留在需要查看的局面，然后照常开始分析。

程序会收集当前节点的主线下一手和所有直接变化分支。普通分析中已经达到
门槛的落点不再补算；不足的落点使用 KataGo 的 `kata-analyze … allow … 1`
依次补足，然后恢复普通分析。例如门槛为 1000，某个点已有 300 visits，
目标是至少达到 1000，而非额外追加 1000。引擎输出有间隔，最终值可能略超门槛。

补算期间保留普通分析的其他选点；补算结果不会被下一次普通分析刷新抹去，
也不会用于覆盖普通搜索的整体胜率、目差、形势判断或总 visits。
棋谱分支选点用紫色小点标识，并可超出正常候选点数量和计算量比例的显示限制。
停一手也会补算，但棋盘上没有对应的交叉点标记。

第一版支持 KataGo 的单引擎交互分析，不要求支持新版 `focus` 参数。
手动指定/排除选点、双引擎、人机对局、引擎对局、自动/批量分析不启用补算。
只分析直接下一手，不递归扫描整棵棋谱。非当前行棋方的变化、虚拟节点和已占用
交叉点不作为补算目标。引擎拒绝补算命令时记录错误并恢复普通分析，不将失败视为达标。

切换局面、暂停分析、重新设置或更换引擎会取消当前补算任务；下一次正常分析时
重新检查目标。删除正在补算的分支会在收到下一次分析更新时重建任务。
补算会占用正常搜索的时间；分支越多、门槛越高，耗时越长。它会先完成分支最低量，
再执行普通交互分析的停止限制；用户仍可随时手动暂停。KataGo 自身的搜索资源限制
也可能影响补算，请勿将其 pondering 上限设得低于所需门槛。

## 构建与验证

构建时跳过仓库默认会改写源码的格式化步骤：

```sh
mvn -Dfmt.skip=true package
```

本仓库的 Surefire 配置默认跳过测试；上面的命令会编译测试，但不会执行测试。
可直接运行新增的 JUnit 检查：

```sh
java -cp target/test-classes:target/lizzie-yzy2.5.3-shaded.jar \
  org.junit.runner.JUnitCore featurecat.lizzie.analysis.BranchAnalysisTest
```

界面与协议集成检查需使用有图形支持的 Java，并设置有效的 `DISPLAY`。
请在仓库外的独立运行目录执行，避免生成配置文件或影响个人设置：

```sh
java -cp /path/to/lizzieyzy/target/test-classes:/path/to/lizzieyzy/target/lizzie-yzy2.5.3-shaded.jar \
  org.junit.runner.JUnitCore featurecat.lizzie.analysis.BranchAnalysisIntegrationTest
```

可选的真实引擎检查另需指定 Java 系统属性 `katago.binary`、`katago.model`、
`katago.config`（均为本机文件的绝对路径）；不指定时仅跳过真实引擎这一项。
它会启动 KataGo GTP 进程，检查两个落点分支及停一手各达到至少 8 visits。
独立运行目录中应已有完成首次设置的配置，可禁用首次设置、配置当前 hostname，
并选择空引擎启动，避免首次使用对话框阻塞检查。

# NekoVideo 交接文档（搜索 / 索引 / 扫描修复）

> 生成时间：2026-10-30
> 仓库：fork `nerve7717/NekoVideo`，上游 `qixishunzi/NekoVideo`（MIT）
> 上游基线：`ffb7d8b`（`main`）
> 提交身份：`nerve7717 <177991175+nerve7717@users.noreply.github.com>`

---

## 0. 30 秒速览

在本仓库的两个功能分支上，向原作者提交了四块改动：

1. **本地媒体库按层级浏览文件夹 + 四种排序**（`feature/library-tree-sort`）
2. **媒体库搜索：模糊 / 精确，范围可选全部或当前文件夹**（`feature/library-search`）
3. **本地索引 + 增量扫描**（启动不再全量重建，537 个视频从“每次全量”变为“只查变动行”）
4. **记住上次所在分页与文件夹 + 选视频入库 + SAF 目录持久化 + MediaStore 变更监听**

`feature/library-search` 是 PR #1 的 head；`feature/library-index` 叠在它上面，等 #1 合并后再提 PR #2。

---

## 1. 提交结构

```
52a168a  Find new videos without a restart, and keep hand-picked files in the library   ┐
66a95f0  Reopen the library where the user left it                                       │ feature/library-index
f4fd0dc  Remember the scanned library and update it incrementally                        ┘
f01b389  Document the library search in both READMEs                                     ┐
0e216af  Add fuzzy and exact search to the local library                                 │ feature/library-search（PR #1）
ac78956  Browse local folders as a tree and add library sorting                          ┘
ffb7d8b  chore: refresh repository stats                                                 ← 上游 main
```

- PR #1：`nerve7717:feature/library-search` → `qixishunzi:main`（3 commits）
- PR #2：**必须等 #1 合并后**再提，见第 7 节。

---

## 2. 各改动的落点

### 2.1 文件夹树 + 排序（`ac78956`）

| 文件 | 作用 |
|---|---|
| `network/source/LocalSourceProvider.kt` | `loadFolders()`：按父目录分桶 → 求最深公共祖先 → 沿祖先链收集节点 → 单链折叠 → 卷根不显示；产出 `totalVideoCount / totalSize / totalDuration / latestModifiedTime` |
| `model/MediaModels.kt` | `MediaFolder` 扩展字段 + `MediaSort(field, ascending)` |
| `util/NaturalOrder.kt` | 数字感知比较器（`2.1.9 < 2.1.10`、`第2章 < 第10章`） |
| `util/PathUtils.kt` | `parentDirectoryOf / isUnderDirectory / isStorageVolumeRoot / commonAncestorDirectory` |
| `settings/AppSettings.kt` | `librarySort` 持久化 |
| `ui/library/*` | `LibraryViewModel.rebuild()` 集中排序；`LibraryScreen` 文件夹网格与排序弹窗 |

### 2.2 搜索（`0e216af`、`f01b389`）

- `util/MediaSearch.kt`：`normalize()`（小写 + 全角→半角 + 只保留字母数字）、`SearchMode.FUZZY/EXACT`；模糊模式 ≥3 字符才启用子序列匹配，短词退回子串。
- `ui/library/LibraryViewModel.kt`：`SearchScope`、`SearchState`、`searchResults`；过滤插在 `rebuild()` 内、`sortedVideos()` 之前，因此搜索结果天然沿用当前排序。
- `ui/library/LibraryScreen.kt`：顶栏搜索图标 + 浮层输入框 + 「模糊/精确」「全部/当前文件夹」四个胶囊；结果复用 `VideoList`。
- 纯内存过滤，不碰 Room / MediaStore，零新依赖。

### 2.3 索引与增量（`f4fd0dc`）

| 文件 | 作用 |
|---|---|
| `database/MediaIndexDatabase.kt` | **独立数据库** `neko_media_index.db`（表 `local_media`，含字幕候选 JSON）+ 实体↔`MediaItemInfo` 映射 |
| `util/MediaIndex.kt` | 纯 Kotlin 增量判定：`changedIds()` / `removedIds()`，SAF 前缀不参与比对 |
| `network/source/LocalSourceProvider.kt` | `sync(known, force)`：先查 `_id + date_modified` 两列做 diff，再按 400/批取完整行；`SubtitleDirCache` 把“每视频一次 listFiles”改为“每目录一次” |
| `repository/MediaRepository.kt` | `indexedLocalVideos()` / `syncLocalVideos()` / `cacheLocalVideos()`，返回 `LocalSyncOutcome(changed)` |
| `ui/library/LibraryViewModel.kt` | 两段式：先渲染索引 → 后台增量；无变动则不重建文件夹树 |

**为什么用独立数据库**：主库 `AppDatabase` 开的是 `fallbackToDestructiveMigration(dropAllTables = true)`，加表就等于升版本，用户的历史/收藏/服务器会被清空。索引是纯缓存，删掉只会触发一次全量重扫，风险与收益不对等。

### 2.4 位置记忆 + 扫描修复（`66a95f0`、`52a168a`）

- `settings/AppSettings.kt`：`libraryTab`、`libraryFolderPath`、`librarySafTrees`（换行分隔的 tree URI 列表）。
- `LibraryViewModel.restoreNavigation()`：**每进程只执行一次**，恢复分页与文件夹；`persistNavigation()` 在切 tab / 进出文件夹 / 导入后写回。
- `importPickedVideo()`：选中的单视频入库，并给合成路径 `Manual/<文件名>` —— 没有 `path` 的条目会被 `loadFolders()` 直接跳过，这正是“只在最近播放里能看到”的根因。
- `walkSafTrees()` + `rememberSafTree()`：手动添加的 SAF 目录持久化并每次扫描重新遍历，这是 MediaStore 没收录时的可靠路径。
- `ContentObserver`（1.5 s 防抖）+ `onCleared()` 反注册：系统收录新文件后自动增量刷新，不必杀进程。
- `importTree()` 成功后切到「文件夹」栏；两个空态补「选择文件夹」按钮。

---

## 3. 构建环境（Windows 必读）

| 事项 | 说明 |
|---|---|
| **路径不能含中文** | 项目放在 `E:\下载\...` 这类路径下，`compileDebugKotlin` 能过，但 `testDebugUnitTest` 会**全部 ClassNotFound**（连原作者自带的测试也挂）。必须在纯 ASCII 路径构建，例如 `robocopy 源 E:\NekoVideo /E /XD build .gradle .git` |
| SDK | `local.properties` 写 `sdk.dir=C\:\\Users\\<你>\\AppData\\Local\\Android\\Sdk`（该文件已被 `.gitignore` 排除）|
| AGP 路径检查 | 若在非 ASCII 路径下想强行构建，可在 `~/.gradle/gradle.properties` 加 `android.overridePathCheck=true`（只消除报错，测试仍会挂）|
| 命令 | `gradlew.bat :app:testDebugUnitTest` / `:app:assembleDebug` / `:app:assembleRelease` |
| 签名 | 本地 keystore 在 `E:\neko-clean\keys\neko-release.jks`（口令在同目录 `keystore.properties`）。**不在仓库内**，丢了就无法覆盖升级已装版本 |
| 出包流程 | `assembleRelease` → `zipalign -p 4` → `apksigner sign --min-sdk-version 24` → `apksigner verify` |
| 安装 | 与手机上已装版本签名一致时 `adb install -r` 直接覆盖，数据不丢 |

---

## 4. 测试

`gradlew :app:testDebugUnitTest` —— **28 例全绿**：

| 测试类 | 例数 | 覆盖 |
|---|---|---|
| `MediaSearchTest` | 11 | 大小写、全角/半角、`[]()` 分隔符、`01 1v1` 精确命中、`1v咨询` 模糊命中/精确不命中、中文片段、路径回退、空查询、超长查询、null |
| `MediaIndexTest` | 6 | 新增/时间戳变动、无变化、空索引退化为全量、force、SAF 不被判删除、前缀识别 |
| `NaturalOrderTest` | 4 | 数字自然序、零填充、忽略大小写、反向 |
| `PathUtilsTest` | 6 | 父目录、层级、公共祖先、卷根 |
| `ExampleUnitTest` | 1 | 模板 |

> 注意：PR 描述里若写 29 例，属于笔误，实际 28。

---

## 5. 已知边界（不是 bug）

1. **MediaStore 未收录的文件，App 扫不到**。`READ_MEDIA_VIDEO` 只给 MediaStore 的可见视图，没有 `MANAGE_EXTERNAL_STORAGE` 就无法直接读任意目录。因此新拷贝的文件夹“第二天才出现”属于系统索引延迟；本分支的对策是 `ContentObserver`（等它收录）+ 手动 SAF 目录（绕过它）。
2. **网络源（WebDAV/SMB/SFTP/FTP）不参与搜索与索引**，本期范围之外。
3. **索引是缓存**：`neko_media_index.db` 损坏或版本不符时，删除该文件即可强制全量重扫，不影响用户数据。
4. 主库仍有 `fallbackToDestructiveMigration(dropAllTables = true)`（原作者既有行为，未改动）——**不要**在主库上加表。
5. `RemoteClients.kt` 的 SFTP 使用 `PromiscuousVerifier()`、`network_security_config.xml` 全局允许明文（均为原作者既有行为，未改动）。

---

## 6. 真机验收清单

- [ ] 文件夹栏按层级进入子目录，返回上一级正常
- [ ] 排序四项（名称/时间/大小/时长）升降序正确，重启后记忆
- [ ] 搜索：`1v1`、`咨询`、`操作系统` 有结果；`1v咨询` 在模糊命中、精确不命中
- [ ] 退出搜索回到原列表且排序不变
- [ ] 杀进程重开后回到上次所在分页与文件夹
- [ ] 选视频后，该视频同时出现在「全部视频」与 `Manual` 文件夹
- [ ] 手动添加文件夹后自动跳到文件夹栏，重启后仍在
- [ ] 拷贝新视频到已有目录，几秒内列表自动出现（或点刷新）

---

## 7. PR 流程

**PR #1 已提交**（`feature/library-search` → `qixishunzi:main`）。

作者合并 #1 之后，把 #2 搬到新 main 再提：

```powershell
git fetch origin
git rebase --onto origin/main f01b389 feature/library-index
git push fork feature/library-index --force-with-lease
```

然后在 GitHub 上开 PR：#2 的 base 必须是 **`qixishunzi/NekoVideo : main`**，head 是 `nerve7717:feature/library-index`，页面应显示 **3 commits**（52a168a、66a95f0、f4fd0dc）。

**注意事项**

- 上游**无法**做 stacked PR：base 仓库必须是目标仓库，作者仓库里没有你的 `feature/*` 分支，把 base 指向自己 fork 的分支只会建成 fork 内部 PR，作者收不到通知。
- 不要用 `cherry-pick` 搬运，会在历史里留下两份同样的改动；`rebase --onto` 才是单线。
- 合并后记得 `git branch -d feature/library-tree-sort`（内容已包含在 `feature/library-search` 里）。

---

## 8. 后续可做（未实现）

1. **搜索高亮**：`buildAnnotatedString` + `SpanStyle`；注意归一化后索引会偏移，需建立映射。
2. **网络源搜索 / 索引**：WebDAV 列表可缓存到同一套索引结构。
3. **搜索输入防抖**：当前是即时过滤（537 条足够快），上万条时加 200 ms debounce + `Dispatchers.Default`。
4. **文件夹重命名/移动检测**：目前靠 `modifiedTime` 变化触发重查，重命名可能只改路径。
5. **主库加迁移**：若要消除 `fallbackToDestructiveMigration` 的清库风险，需补 `Migration(2,3)` 等真实迁移。

---

## 9. 交接包内其它产物

| 路径 | 说明 |
|---|---|
| `apk/NekoVideo-search-release.apk` | 含文件夹树 + 排序 + 搜索 |
| `apk/NekoVideo-index-release.apk` | 上述 + 索引增量 + 位置记忆 |
| `apk/NekoVideo-scanfix-release.apk` | 上述 + 扫描修复（当前最新，已装手机验证） |
| 仓库外 `keystore.properties` + `neko-release.jks` | 本地签名材料，**勿提交** |

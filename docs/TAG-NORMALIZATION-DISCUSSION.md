# 标签归一（翻译规则 / 服务器元数据 / APP 抓取元数据）设计讨论稿

- 日期：2026-09-15
- 性质：**只读分析稿**。不修改任何源码；本文所有结论都标注来源与置信度。
- 目标：回答「把翻译规则、服务器元数据、APP 抓取到的元数据统一起来，写回服务器实现归一，同时保留输入英文经翻译规则联想中文」这条路怎么走。
- 事实基线：
  - APP：`D:\program\lanraragi-reader-merged`（Kotlin / Compose）
  - 服务端权威：`D:\program\LANraragi-v.0.9.81\LANraragi-v.0.9.81`（Perl，LANraragi 0.9.81）
  - 词库：EhTagTranslation（`db.text.json` / `db.html.json`）

**阅读约定**：`[事实]` = 已在代码/契约中逐行核实；`[推断]` = 由已核实事实推出，标注依据；`[不确定]` = 本机无法核实。

---

## 1. 现状勘察

### 1.1 翻译规则侧（词库）

**实体字段**

`[事实]` `data/tags/knowledge/TagKnowledgeModels.kt:12-20` 定义词典行（`namespace`=canonical 英文命名空间、`tagKey`=英文原 key 小写、`translatedName`=EHT 的 `name` 即中文译名、`fullName`=EHT 的 `fullName` 全名形态、`intro`/`links`/`dataVersion`=词库快照版本）。

`[事实]` `knowledge/TagKnowledgeModels.kt:8-10` 稳定键 `TagKnowledgeKey(namespace, tagKey)`，`fullName` 属性即 `"ns:key"`。`TagKnowledgeModels.kt:22-28` 另有 `TagFrequencyRecord(namespace, tagKey, source, count, dataVersion)`。

`[事实]` `knowledge/TagKnowledgeModels.kt:34-43` 快照溯源 `TagKnowledgeSourceMetadata(sourceUrl, retrievedAt, schemaVersion, license, attribution, eTag, lastModified, checksumSha256)`，`validated()`（:44-62）要求 `checksumSha256` 匹配 `[0-9a-fA-F]{64}`。

**canonical 化规则**

`[事实]` `data/tags/TagNamespaceRegistry.kt:157-162` 命名空间归一：`Normalizer.NFKC → trim → \s+ 折叠为单空格 → lowercase(Locale.ROOT)`。

`[事实]` `TagNamespaceRegistry.kt:95-98` `canonicalNamespace()` 查别名索引，**未知命名空间原样保留（不丢弃用户数据）**。

`[事实]` `TagNamespaceRegistry.kt:144-155` 别名索引 `buildAliasIndex` 只登记 `descriptor.name`（canonical 英文名）与 `descriptor.aliases`，**不登记 `labelZh`**。这是后面「中文命名空间认不出来」的根因。

`[事实]` `TagNamespaceRegistry.kt:44-78` 内置 24 个命名空间，含 `labelZh` 与别名：
`parody/原作, character/角色, group/社团(groups,circle,circles), artist/作者(artists), female/女性(females), male/男性(males), mixed/混合, language/语言(languages,lang), cosplayer/Coser, reclass/重分类(reclassify,reclassification), temp/临时, uploader/上传者, collection/收藏集, convention/展会, location/地点, other/其他, source/来源(hidden), date/日期, date_added/添加日期(hidden), series/系列, category/分类, event/活动, timestamp/时间戳(hidden)`。

`[事实]` `TagNamespaceRegistry.kt:104-113` 未知命名空间得到 fallback descriptor：`labelZh = 原名`、灰色 `#9E9E9E`、`displayOrder = Int.MAX_VALUE`。

`[事实]` `knowledge/TagKnowledgeModels.kt:91-105` 词典行归一：`namespace` 走 `canonicalNamespace`，`tagKey` 走 NFKC + lowercase，空 key 直接 `require` 失败。

`[事实]` `knowledge/TagQuery.kt:56-62` 查询解析器**额外**把 `labelZh` 精确匹配成 canonical 英文名（`allDescriptors().firstOrNull { it.labelZh == normalized }`）——这是唯一一处「中文命名空间 → 英文 canonical」的可用通路。

**词库来源与解析**

`[事实]` `data/TagTranslationStore.kt:74` 下载地址 `https://github.com/EhTagTranslation/Database/releases/latest/download/db.text.json`。

`[事实]` `knowledge/EhTagTranslationParser.kt:46-73` 支持 5 种根形态：JSON 数组、`{namespace, data}`、`{data: [...]}`、`{data: {ns: {...}}}`、`{ns: {...}}`；`:185` 顶层元数据键 `version/schemaversion/updatedat/license/source` 会被跳过。

`[事实]` `knowledge/EhTagTranslationParser.kt:139-165` 行字段映射：`namespace` + `data.{tagKey}.{name, fullName|full_name, intro, links}`；`:156` `name` 与 `tagKey` 大小写不敏感相等时**丢弃** translatedName（译名等于原文即视为无译名）。

`[事实]` `knowledge/EhTagTranslationParser.kt:105-107, 117-126` 导入做 SHA-256 校验，checksum 不匹配立即拒绝；空词典拒绝。

`[事实]` `[不确定]` EHT 上游是否存在 `rows` 伪命名空间（含 `ns:tag` 全名行）——本机无 `db.text.json`，未核实。代码只显式排除了 `META_NAMESPACE = "namespace"`（`TagTranslationStore.kt:329, 525`），**没有排除 `rows`**。若 `rows` 存在，`buildNormalizationIndex`（`TagTranslationStore.kt:325-344`）会把 `rows` 里的行也塞进 `translated` 索引，第 2 步命中时会拼出 `rows:artist:xxx` 这种非法查询串。**建议在动手前先下载一次 `db.text.json` 核实。**

**存储体积与表结构**

`[事实]` `data/db/Entities.kt:141-155` `tag_dictionary`，主键 `(namespace, tagKey)`，索引 `translatedName`、`updatedAt`。
`[事实]` `Entities.kt:158-167` `tag_dictionary_fts`，`@Fts4`，列为 `canonicalKey/namespace/tagKey/translatedName/fullName/intro`。
`[事实]` `Entities.kt:169-181` `tag_frequency`，主键 `(namespace, tagKey, source)`，索引 `count`。
`[事实]` `data/db/Daos.kt:185-194` FTS 查询用 `INNER JOIN` 回 `tag_dictionary`。
`[事实]` `data/tags/knowledge/TagKnowledgeRoomAdapter.kt:56-90` 激活是**全清 + 全量重写三张表**（`clearDictionarySync`/`clearFrequenciesSync`/`clearSearchIndexSync` → `replace*Sync`），单事务内完成。
`[事实]` `TagKnowledgeRoomAdapter.kt:40-42, 58-61` 版本必须严格递增（`compareTagKnowledgeVersions`，`:136-149` 按 `.`/`-`/`_` 切段、数字段数值比较），否则拒绝。→ 同一份词库重复导入不会重复写表。
`[事实]` `TagTranslationStore.kt:424-443` `publish()` 额外把快照压成 `namespace(lowercase) -> (tag -> 译名)` 的紧凑映射，落到 `filesDir/tag_translations.json`（:70），供 `TagChip` 展示层使用。
`[事实]` `TagTranslationStore.kt:389-422` 自动更新：开关 `tagTranslationAutoUpdate`（默认 **false**，`SettingsRepository.kt:162`），7 天过期，进程内只触发一次。

**更新与失效路径**

`[事实]` 下载 → `EhTagTranslationParser.parseJson` → `TagKnowledgeRepository.replace`（`TagKnowledgeRepository.kt:23-30`）→ `RoomTagKnowledgeStore.activate`（事务）→ 缓存失效（`loaded=false`）→ `publish` 重发 `TagTranslationStore.translations`（StateFlow 实例身份变化）→ `TagTranslationRepository` 的归一化索引缓存（`TagTranslationStore.kt:83-84, 302-312`）因源对象身份变化而重建。
`[事实]` `TagTranslationStore.kt:105-176` 更新走 ETag/Last-Modified 条件请求，304 时复用当前快照。

**已完成但未接线的部分（关键）**

`[事实]` `TagTranslationStore.kt:198-207` `normalizeSearchQuery()` 与 `:213-225` `normalizeSearchPreview()` 是「查询 token 归一化」的完整实现（三步：原文精确 → 中文译名 → 英文前缀兜底，`:228-287`），但**全仓库（含测试）没有任何调用方**（`grep normalizeSearchQuery|normalizeSearchPreview` 仅命中定义处）。即 `docs/enhancement-plan-2026-09-13.md:81` 的 D4 只落地了数据层，**搜索链路未接线**。

`[事实]` `TagKnowledgeRepository.kt:32-57` 联想的真实调用路径是 `FTS 候选 → TagSuggestionRanker`；FTS MATCH 语法由 `knowledge/TagKnowledgeFtsQuery.kt:8-12` 生成，词长 < 2 或含非字母数字就返回 null → 回落全量扫描。
`[事实]` `TagSuggestionRanker.kt:61-80` 排序质量枚举：`NAMESPACE_EXACT(500) > NAMESPACE_PREFIX(450) > CANONICAL_PREFIX(400) > TRANSLATED_PREFIX(320) > CANONICAL_CONTAINS(220) > TRANSLATED_CONTAINS(140)`；`:86-95` `namespaceTerms` 含 `labelZh`，因此**单独输入「作者」这类中文命名空间名会把该命名空间下的英文标签顶到最前**。

**展示层**

`[事实]` `ui/components/TagChip.kt:50-56` `rememberTagText(ns, value)`：`translations[ns][value]` → `translations[ns][value.lowercase()]` → 原名。**只按 (命名空间原文, 值) 查表；中文命名空间、中文值都查不到，于是原样显示**。
`[事实]` `TagChip.kt:65-136` `TagRules` 的 `NAMESPACE_ORDER`/`NAMESPACE_LABELS`/`NAMESPACE_COLORS` 全是 `TagNamespaceRegistry.allDescriptors()` 的投影；`nsOf()` 走 `canonicalNamespace`；`formatValue` 只对 `date_added` 做时间戳→日期。
`[事实]` `TagChip.kt:102-120` `groupTags`：内置命名空间按 `displayOrder` 排前，未知命名空间按字典序排在**全部内置项之后**，无命名空间排最后。
`[事实]` `ui/components/TagChip.kt:21-23, 41-46, 96-99` 颜色三级回退：用户覆盖（DataStore）→ registry 默认色 → 灰。

### 1.2 服务器元数据侧

**读**

`[事实]` `data/api/LanraragiApi.kt:36-37` `GET api/archives/{id}/metadata`。
`[事实]` `Api/Archive.pm:48-61` `serve_metadata` → `get_archive_json`；`Utils/Database.pm:274-279` `build_json` 的 `tags` 是**已存储的逗号串**（`$tags // ""`）。
`[事实]` `data/model/Models.kt:30-31` APP 侧 `Archive.tagList = tags.split(',').map(trim).filter(notEmpty)`。
`[事实]` `data/api/LanraragiApi.kt:57-58` `GET api/database/stats`；`data/model/Models.kt:72-78` `TagStat(namespace?, text, weight)`，`full = "ns:text"`。
`[事实]` `Model/Stats.pm:222-255` 统计直接从 Redis `LRR_STATS` 取，`redis_decode` 后按**第一个 `:`** 拆成 namespace/text，`weight` 就是分数。

**写**

`[事实]` `data/api/LanraragiApi.kt:71-77` `PUT api/archives/{id}/metadata`，`title`/`tags`/`summary` **都是 query 参数**（`@Query`）。契约一致：`LANraragi-v.0.9.81\tools\openapi.yaml:1029-1067`，描述原文 "Data supplied to the server through this method will **overwrite** the previous data."
`[事实]` `Api/Archive.pm:338-364` `update_metadata`：`$self->req->param('tags')`，整个写操作被 `exec_with_lock($self, "archive-write:$id", ...)` 串行化。
`[事实]` `Model/Archive.pm:296-324` 服务端语义：`trim` + `trim_CRLF` 后才用；**只有 `defined $tags` 才调用 `set_tags`**，`summary` 同理（`:315-317`）。即「不传 = 不改」。
`[事实]` `Utils/Database.pm:460-495` `set_tags`：`$newtags = join_tags_to_string( uniq( split_tags_to_array($newtags) ) )` → **服务端自己做去重、保留首次出现顺序、不做排序**；`$append` 默认 0（覆盖），PUT 路径不会传 append。`Utils/Tags.pm:39-51` `split_tags_to_array` 按 `,` split 后逐条 `trim`，`join_tags_to_string` 用 `,` 连接。

**服务端规范化 / 大小写 / 去重 / 转义**

`[事实]` `Utils/Redis.pm:19-23` `redis_encode = NFC($data) + encode_utf8`。存储只做 **NFC**。
`[事实]` `Utils/Database.pm:530-588` `update_indexes`：对每条标签**取小写 `lc($tag)`** 再 `redis_encode`，作为 `INDEX_<lc tag>` 集合成员与 `LRR_STATS` 分数键；同时维护 `LRR_URLMAP`（`source:` 标签）与 `LRR_UNTAGGED`。
  → 索引键必然小写；**存储串保留原大小写**。APP 侧 `CanonicalTag` 的 identity 也小写，方向一致。
`[事实]` `Model/Search.pm:388-474` `compute_search_filter`（查询语法）：
  - 分隔符是 **逗号 `,`**（不是空格；引号包裹时以 `"` 为分隔并置 `isexact`）；
  - token 前缀 `-` = 排除；
  - token **尾部** `$` = 精确（`$` 被吃掉），整个 token 用双引号包裹也 = 精确；
  - 转义：`[`, `]`, `^`, `\` 前面加反斜杠（`:455`）；`_` → `?`（单字符通配）；`%` → `*`（多字符通配）（`:459-462`）；
  - token 最终 `lc()`（`:466`）。
`[事实]` `Model/Search.pm:280-299` 匹配语义：
  - `isexact` **且** `INDEX_<tag>` 存在 → 直接 `smembers`（真精确）；
  - 否则 → `keys(INDEX_*<tag>*)`（**无命名空间 = 全命名空间子串匹配**）或 `keys(INDEX_<tag>*)`（**有命名空间 = 前缀匹配**）。
  → 结论：**「精确」只是快路径**。`artist:foo$` 在 `artist:foo` 不存在时会退化成前缀 glob，可能命中 `artist:foobar`。无命名空间的 `foo$` 也会退化成 `INDEX_*foo*`。
`[事实]` `Model/Search.pm:301-322` **每个 token 都额外附带一次标题模糊检索**（`LRR_TITLES` zscan：精确时 `<tag>\x00*`，非精确时 `*<tag>*`）。→ 标签条件同时会命中标题含该词的档案。
`[事实]` `Model/Search.pm:334` 条件之间是 `intersect_arrays(..., $isneg)`：**AND 交集 / 差集，全链路没有 OR**。
`[事实]` `Model/Search.pm:476-627` 排序 `sort_results`：`my $re = qr/$sortkey/`，在**原始 tags 串**上做**大小写敏感**匹配 `m/.*${re}:(.*?)(\,.*|$)/` 取该命名空间的值；无该命名空间 → 记 `"zzzz"` 并**统一排到结果末尾**（`:606-621`）。
`[事实]` `Model/Config.pm:221` 默认 `excludednamespaces = "source, date_added"`；`Api/Other.pm:42-43` 通过 `/api/info` 暴露。
`[事实]` `Model/Plugins.pm:271-291` 元数据插件结果的标签处理：`split_tags_to_array` → （`enable_tagrules` 时）`rewrite_tags` → 与档案已有 tags 做 **`index(uc($tags), uc($tagtoadd))` 子串去重** → 产出 `new_tags`。
`[事实]` `Utils/Tags.pm:53-146` 服务端标签规则类型：`remove`（整 tag 全等，`lc` 比较）/`remove_ns`（`ns:*` 整命名空间）/`strip_ns`/`replace_ns`/`replace`（整 tag 替换）/`hash_replace`（`match => value`，按 `lc $tag` 精确替换）。
`[事实]` `Controller/Batch.pm:50-66, 151-177` **批量应用 tagrules 到既有档案只存在于 WebSocket 批量页**（`/batch`），`openapi.yaml` 中**没有**对应 REST 端点（grep `batch|tagrules` 只命中 `/database/backup`）。→ **APP 无法通过 REST 触发服务端批量标签重写。**
`[事实]` 服务器元数据插件产出的命名空间：`Plugin/Metadata/EHentai.pm:250-256` `category:<lc cat>`、可选 `uploader:`/`timestamp:`，其余为 E-Hentai 原始标签（**英文命名空间英文值**）；`Plugin/Metadata/DateAdded.pm:55-58` `date_added:<ts>`。

**备份 / 回滚能力**

`[事实]` `openapi.yaml:2448-2473` `GET/POST /database/backup`、`:2545` `/database/backup/{jobid}`、另有 restore。
`[事实]` APP 已实现：`data/LanraragiRepository.kt:765-776` `queueBackup()`、`:779-787` `downloadBackup()`、`:796-814` `restoreBackup()`（multipart 上传 → Minion 排队）。

**分类**

`[事实]` `data/model/Models.kt:82-88` `Category(id, name, search, pinned, archives)`；服务端动态分类的 `search` 字段走同一套 `compute_search_filter`（`Model/Search.pm:149-151`）。→ 归一会**使服务器上已保存的动态分类条件失效**。
`[事实]` APP 只能创建/重命名/删除分类与增删档案（`LanraragiApi.kt:84-105`），**不能编辑 `search`**。

### 1.3 APP 抓取元数据侧

**抓回来的标签长什么样**

`[事实]` 原生 nHentai：`providers/NativeMetadataFetch.kt:156-161` 取 `tags[].type` + `tags[].name` → `CanonicalTag.parse("$namespace:$name")`。`type` 是 nHentai 的英文枚举（`language/female/male/tag/artist/character/parody/group/category`）。
`[事实]` 原生 E-Hentai：`NativeMetadataFetch.kt:170-172` 直接 `CanonicalTag.parse(tag)`，标签形如 `artist:foo`、`female:glasses`、`language:chinese`（英文命名空间 + 英文值）。
`[事实]` 服务器插件：`plugins/ServerMetadataPluginCoordinator.kt:388-392` `newTags.split(',').map(trim).map { CanonicalTag.parse(it, TagSource.LANRARAGI) }`。
→ **三路抓取来源全部产出英文命名空间 + 英文值**；只有服务器库本身是中英双轨。

**`MetadataPatch` 数据结构**

`[事实]` `domain/metadata/MetadataPatch.kt:22-35`：`title`/`summary`/`sourceUrl` 各为 `MetadataField<String>?`，`addTags: Set<CanonicalTag>`、`removeTags: Set<CanonicalTag>`、`tagProvenance: Map<String, MetadataProvenance>`；构造断言同一 canonical tag 不能同时 add/remove。
`[事实]` `MetadataPatch.kt:3-15` `MetadataProvenance(providerId, sourceId, sourceUrl, confidence, fetchedAt, dataVersion)`。
`[事实]` `domain/metadata/CanonicalTag.kt:15-34`：`identity = CanonicalTagKey(ns?.normalize()?.lowercase(), key.normalize().lowercase())`，`full = "ns:key"`（**全部小写**）；`parse()`（`:42-52`）的 `raw` 字段保存**原样字符串**（连 trim 都不做）。
`[事实]` `CanonicalTag.kt:54-57` `normalize = NFKC + trim + \s+ 折叠`。
`[事实]` `data/metadata/MetadataPatchMerger.kt:23-35` `MetadataSnapshot.tags: Set<CanonicalTag>`，`canonicalTags = tags.associateBy { it.full }`（**小写 full 为身份键**）。

**合并链路**

`[事实]` `MetadataPatchMerger.kt:67-71` 默认策略：`overwriteExistingFields=false`、`preserveUserOverrides=true`、`protectUserTags=true`。
`[事实]` `MetadataPatchMerger.kt:191-260` 标签增删：若 `MetadataFieldName.TAGS ∈ userOverrides` → 所有 add 转 `unapplied` 并记 CONFLICT；`TagSource.USER` 的既有标签**禁止被 patch 删除**。
`[事实]` `MetadataRepository.kt:527-559` `withClientAnnotations`：每次刷新服务端快照时，`userOverrides` **只增不减**（`:556`）。→ 用户手工编辑过标签的档案会**永久**带上 TAGS 覆盖标记。
`[事实]` `data/metadata/MetadataApplyPlanner.kt:104-125` `tagsForPut()`：把 `merged.canonicalTags` **整体**作为 PUT 载荷（含本次未参与合并的既有标签），并按 `source:` 规则同步来源标签。
`[事实]` `MetadataApplyPlanner.kt:66-91` 阻断原因：`TARGET_IS_NOT_REMOTE` / `BASE_CHANGED_REVIEW_REQUIRED` / `UNAPPLIED_PATCH_REVIEW_REQUIRED` / `NO_EFFECTIVE_CHANGES`。
`[事实]` `MetadataRepository.kt:172-357` `applyPending`：**先 GET → 规划 → PUT → 再 GET 并用 `matchesPutPayload` 校验指纹**（`:565-582`）。写入失败/校验失败都不静默。
`[事实]` `data/metadata/MetadataAdapters.kt:32-39` PUT 序列化：

```kotlin
tags = payload.tags.sortedBy { it.full }.joinToString(",") { it.raw.ifBlank { it.full } }
```

即**排序按小写 canonical full，发送时用 raw（原大小写）**。
`[事实]` `domain/metadata/MetadataDiff.kt:114-148` 指纹基于 `CanonicalTag.full`（小写）去重排序集合。→ 服务端只把大小写改了（内容等价），APP **不会**判为 baseChanged。

**是否去重 / 排序 / 翻译**

`[事实]` 去重：PUT 载荷经 `MetadataSnapshot.canonicalTags` 的 `associateBy { full }` 天然去重（小写 full）；但 `MetadataSnapshot.tags` 只按整值去重（`MetadataSnapshots.kt:8` 用 `linkedSetOf<CanonicalTag>`），所以 `Artist:X` 与 `artist:x` 会**同时**留在快照里，直到 PUT 才被折叠。
`[事实]` 排序 `sortedBy { it.full }`（`MetadataAdapters.kt:37`，服务端 `join` 保序不再排序）；**翻译完全不做**——`data/metadata/**` 全目录没有引用 `TagTranslationStore`/词库（grep 确认）。

**D6 中文向导（已存在，但绕过主链路）**

`[事实]` `ui/tools/WritebackScreen.kt` 是一个完整的「扫描 → 预览 → 回写」向导（`AppRoot.kt:163-165` 路由、`SettingsScreen.kt:2705-2720` 入口）。关键实现：
  - `:480-493` `analyzeEnglishTag`：**值必须是全 ASCII 可打印（0x20..0x7E）**才参与；`defaultHidden` 命名空间跳过；命名空间原样保留，只翻值。
  - `:499-514` `lookupTranslation`：命名空间 null → 试 `misc`/`""`；否则试 `ns.lowercase()` 与 `canonicalNamespace(ns)`。
  - `:344-453` `startExecute`：逐本 `getMetadata` → 按 `CanonicalTagKey` identity 替换 → identity + 小写文本双重去重 → **直接调用 `repository.updateArchiveMetadata`**（全量覆盖 PUT，`:410-415`）；每本间隔 500ms（`:467`）；协作式取消（`:456-458`）；`:681-709` 警示卡建议先做服务器备份。
  - **没有经过 `MetadataRepository` / `MetadataApplyPlanner`**：没有 plan、diff、`matchesPutPayload` 写后校验、baseChanged 重基检查、`userOverrides` 登记——它是 `LanraragiRepository` 直连 PUT。

**该向导的能力边界（与用户诉求的差距）**

`[事实]` 它只能做「英文值 → 中文值」，**命名空间不改**：`艺术家:せぶんがー` 因值非 ASCII 被**完全跳过**（`:488`）；`artist:38` 因词典无条目而保留原文。
→ **现有向导既不能把 `艺术家:` 归一到 `artist:`，也不能反向归一中文值。用户诉求中的「统一」它只覆盖了约三分之一。**

### 1.4 现有不一致点（代码/数据层面的确认）

`[事实]` `ui/screens/LibraryScreen.kt:1679-1685` 联想列表 = 词库候选（英文 canonical，来自 `TagKnowledgeRepository.suggestions`）**并接**服务器热门标签（`/api/database/stats` 的实际形态，`:1680-1684`），`distinctBy { namespace to tagKey }` 去重后取 24 条。
`[事实]` 点选后填入查询串的是**词库形**（`full` 为英文 canonical），见 `:220, 233-236, 320-328` → `LibraryScreen.kt:2073-2079` → `SearchQueryCodec.replace`。
`[事实]` `data/catalog/SearchQueryCodec.kt:39-42` `exactTag()` 明确拒绝含 `"`、`,`、`*`、`?`、`_`、`%` 的标签，报错「该标签含有服务器搜索保留符号」。
`[事实]` `SearchQueryCodec.kt:28-33` `validationError` 只拦未闭合引号、`~` 运算符、条件内部裸引号。
`[事实]` `data/catalog/LibraryQuery.kt:55-58` 命名空间排序的 wire 值是**写死的英文**：`title/lastread/date_added/artist/language/series`。
`[事实]` `data/SearchDiscoveryRepository.kt:29-33` 服务器标签统计入库前过 `canonicalNamespace`（对「艺术家」无效，原样保留），并隐藏 `source/date/date_added/timestamp/temp/日期/添加日期/时间戳/临时`，按 `full` 去重、按 weight 降序。

---

## 2. 三类元数据的对照表

### 2.1 命名空间集合对照

| 语义 | 词库/registry canonical | registry `labelZh` | 服务器实况（用户提供，weight=标签数） | 抓取来源产出 | 备注 |
|---|---|---|---|---|---|
| 原作 | `parody` | 原作 | `原作` 61 / `parody` 3 | `parody`（EH/nH） | 一致 |
| 角色 | `character` | 角色 | `角色` 190 / `character` 34 | `character` | 一致 |
| 社团 | `group` | **社团** | **`团队` 222** / `group` 89 | `group` | `[推断]` 服务器用「团队」，与 EhTagTranslation 对 `group` 的惯用译名一致，而 registry 用「社团」；依据：两者语义重合、服务器不存在 `社团`，且 registry 的 `group` 别名里没有 `团队` |
| 作者 | `artist` | **作者** | **`艺术家` 422** / `artist` 132 | `artist` | `[推断]` 同上一行，「艺术家」是 EHT 惯用译名；服务器不存在 `作者` |
| 女性 | `female` | 女性 | `女性` 394 / `female` 8 | `female` | 一致 |
| 男性 | `male` | 男性 | `男性` 171 / `male` 2 | `male` | 一致 |
| 混合 | `mixed` | 混合 | `混合` 6 / `mixed` 1 | `mixed` | 一致 |
| 语言 | `language` | 语言 | `语言` 12 | `language` | 服务器未见 `language`（可能在 minweight 阈值下未出现） |
| 重分类 | `reclass` | **重分类** | **`重新分类` 5** | `reclass` | 用词差异（重分类 vs 重新分类） |
| 地点 | `location` | 地点 | `地点` 2 / `location` 5 | `location` | 一致 |
| 其他 | `other` | 其他 | `其他` 28 / `other` 1 | `other` | 一致 |
| 机器（不动） | `source`/`date_added`/`timestamp` | 来源/添加日期/时间戳（hidden） | `date_added:1788366558`（用户示例）；`source` 默认被 excluded 未统计 | `source`；`timestamp`（EH 插件可选） | 不参与归一 |
| 其他 | `cosplayer`/`series`/`category`/`event` | Coser/系列/分类/活动 | 未出现 | `cosplayer`；`category`（EH 插件） | — |
| 上传者 | `uploader` | 上传者 | 未出现 | `uploader`（EH 插件可选） | — |

### 2.2 三者的差集结论

`[事实]` **词库的命名空间集合是硬编码白名单**（`TagNamespaceRegistry.builtIns`），代码里没有任何「从 EHT 词库派生命名空间集合」的通路。`[推断]` 因此 EHT 一旦新增命名空间，registry 不会自动跟上。
`[事实]` 服务器命名空间必然**全小写**（`Utils/Database.pm:550,570` 的 `lc($tag)`）；中文部分无大小写。→ 用户给出的分布里 `artist`/`group`/`character`/`parody`/`female`/`male`/`mixed`/`location`/`other` 全小写，佐证该结论。
`[事实]` `TagStat.namespace` 拿不到原大小写；`Archive.tagList` 保留原大小写。两者在 APP 内都经 `canonicalNamespace` 小写化，方向一致。
`[推断]` **同一语义在服务器上并存两套写法**（`艺术家`+`artist`、`团队`+`group`、`角色`+`character`、`原作`+`parody`、`女性`+`female`、`男性`+`male`、`混合`+`mixed`、`地点`+`location`、`其他`+`other`、`重新分类`+`reclass`）。依据：上表逐对出现，且 `update_indexes` 不做任何跨命名空间等价折叠。
`[事实]` registry 的 `labelZh` 与服务器中文写法有 **3 处冲突**：`group`=社团/团队、`artist`=作者/艺术家、`reclass`=重分类/重新分类。→ 任何「用 registry labelZh 生成中文命名空间」的做法都会**造出服务器上不存在的第 3 套写法**（`社团:`、`作者:`、`重分类:`）。
`[事实]` registry 的 `labelZh` **不参与** `canonicalNamespace` 的别名索引（`TagNamespaceRegistry.kt:144-155`），所以 `descriptor("艺术家")` 返回 fallback：灰色、排在全部内置命名空间之后。→ 服务器上**最大的命名空间（艺术家 422）在 APP 默认分组里排到最后且无专属颜色**。
`[事实]` 只有 `TagQueryParser`（`TagQuery.kt:56-62`）和 `TagTranslationStore.normalizeSearchToken`（`:236-238`）会用 `labelZh` 把中文命名空间折算成英文 canonical，而且**只认 registry 里那份 labelZh**（「作者」可以，「艺术家」不行）。

---

## 3. 归一的目标形态与取舍

### 3.1 三个候选形态

- **A. 英文 canonical 单轨**：命名空间与值都收敛为 `artist:foo` 这种 E-Hentai/EHT key 形态。
- **B. 中文单轨（推荐方向）**：命名空间用一套固定写法（建议英文 canonical），值用中文译名，即 `artist:巴`。
- **C. 双轨 + 等价关系表**：服务器保持两套，APP 用词库建立等价身份，显示与搜索时合并。**不改服务器**。

> 注意：`docs/enhancement-plan-2026-09-13.md:83,103` 与 `docs/ui-ux-enhancement-plan-2026-09-13.md:187` 已经把 **B（服务端目标形态为中文）** 写进了项目既有规划。本稿不推翻它，而是把它精确化：**值用中文，命名空间建议统一为英文。**

### 3.2 对搜索的影响（无 OR 是关键约束）

`[事实]` 服务端条件之间只有 AND/差集（`Model/Search.pm:334`），**没有办法表达「`艺术家:x` 或 `artist:x`」**。

| 形态 | `artist:x$` | `艺术家:x$` | 裸值 `x` | 影响 |
|---|---|---|---|---|
| A 英文单轨 | 命中 | 空 | 命中 | 英文输入与联想天然可用；**中文输入必须靠客户端归一** |
| B 混合命名空间中文单轨 | 空 | 命中 | 命中 | 中文输入可用；**英文输入全部失效**，必须靠客户端归一 |
| B 英文命名空间 + 中文值 | `artist:中文译名$` 命中 | 空 | 命中 | 需要「英文原值 → 中文译名 → 记住命名空间」三步映射 |
| C 双轨不改 | 只命中英文轨 | 只命中中文轨 | **可能同时命中两轨**（见下） | 语义分散，结果取决于值是否同形 |

`[事实]` **裸值查询是跨命名空间子串匹配**：无 `:` 的 token 走 `keys(INDEX_*<tag>*)`（`Model/Search.pm:290`）→ 值**同形**时（`artist:38` / `艺术家:38`）裸查 `38` 同时命中两轨，过渡期伤害比想象中小；值**不同形**时（`老师` vs `sensei`）只能命中一轨，**客户端无法补救**。
`[事实]` 带命名空间的条件在 `isexact` 且索引存在时是真精确（`smembers`），否则退化为前缀 glob（`Model/Search.pm:280-299`）→ 过渡期 `artist:x$` 可能**误命中** `artist:xy`，用户会看到"精确匹配却不精确"。
`[事实]` 每个 token 还会附带标题模糊检索（`Model/Search.pm:301-322`），因此"标签查不到"往往**不会返回空集**，而是返回标题含该词的档案——**归一前后的差异会被掩盖，用户难以自查**。

### 3.3 对显示的影响

`[事实]` `rememberTagText` 对两种形态都"看起来正确"（英文走词典翻译、中文原样显示），`groupTags` 却把未知命名空间排在全部内置项之后（`TagChip.kt:114-118`）→ 现在 `艺术家:`（422 条）排在最末、灰色。→ **显示层不是问题，分裂才是问题**：同一语义出现两个 chip。
`[推断]` 归一后（英文命名空间 + 中文值）显示反而**更好**：颜色、排序、命名空间中文标题全部回到 registry 主路径。依据：`rememberTagText` 的 fallback 行为。
`[推断]` 但归一目标值在词典里**没有译名可查**（词典 key 是英文），所以"英文 → 中文联想"必须由**词典逆索引**承担。

### 3.4 对写回的影响

`[事实]` `MetadataPutPayload.tags` 是**完整集合**（`MetadataApplyPlanner.kt:104-125`），PUT 是覆盖式（`openapi.yaml:1038`）。→ 归一天然可以"顺带"在写回时发生，不需要额外接口。
`[推断]` 但这也意味着**归一的破坏半径 = 整份标签集**，而不是"新增的那些标签"。任何一次归一写回，如果计算出的集合有偏差，都会**替换掉用户库里的全部标签**。这是本稿里风险最高的一点。
`[事实]` `MetadataAdapters.kt:37` 发送 `raw`（原大小写），只有被翻译的标签才会换成中文。→ 归一实现必须同时决定"值写什么"和"命名空间写什么"。

### 3.5 对回滚的影响

`[事实]` 现有回滚能力只有整库粒度（`queueBackup`/`downloadBackup`/`restoreBackup`，`LanraragiRepository.kt:765-814`）；`WritebackScreen` **完全没有回滚**（不存 before 快照、无批次记录，只有最多 20 条执行日志，`:464`）。
`[推断]` 整库恢复会**覆盖备份之后的所有其它改动**（含网页端的编辑），不适合作为常规回滚 → 必须补 **per-archive before/after 快照 + 批次撤销**。
`[事实]` 归一是**不可逆的信息删除**：`artist:foo` → `artist:巴` 之后英文原形在服务器上消失，此后"输入英文联想中文"只能靠客户端反向映射。

### 3.6 本稿建议

1. **值的形态**：中文（沿用既有规划 B）。理由：`docs/enhancement-plan-2026-09-13.md:103` 已定；显示层零成本；与 EHT 译名体系一致。
2. **命名空间的形态**：**英文 canonical**。理由：`[事实]` 服务端 `sort_results` 用大小写敏感的 `qr/artist/` 在原串上取命名空间值（`Model/Search.pm:576,595`），APP 的排序 wire 值写死为英文（`LibraryQuery.kt:55-58`）；服务器标签规则也以 `lc` 后的 `artist:` 形态工作。用中文命名空间会让**按作者/系列/语言排序静默失效**。
3. **双轨的处置**：过渡期允许"服务器双轨 + 客户端等价表"（形态 C）**只读存在**，但归一写回的目标是单轨。
4. **registry 的 3 处用词冲突**（社团/团队、作者/艺术家、重分类/重新分类）：以服务器实况为准，把服务器中文写法登记为 registry 的 `labelZh` 与 `aliases`。理由：`labelZh` 不参与别名索引（`TagNamespaceRegistry.kt:144-155`），导致现状下「艺术家:」既排序异常又无法被 `canonicalNamespace` 折算。

---

## 4. 写回方案

### 4.1 在哪里做转换（四个插入点）

| 插入点 | 位置 | 优点 | 缺点 | 建议 |
|---|---|---|---|---|
| (a) 抓取结果入库前 | `NativeMetadataFetch`/`ServerMetadataPluginCoordinator` 产出 `CanonicalTag` 之后，`toPatch()` 之前 | 英文原形**永不入库**，从根上避免"每次抓取都要重写全库" | 只影响新标签，历史残留不动 | **采用**（对新增标签） |
| (b) 合并时 | `MetadataPatchMerger` | 可以让 canonical identity 跨语言相等 | 会改变 `latest`/`baseline` 比较语义，动到核心纯函数；且 `MetadataSnapshot` 是服务端镜像，不该被改写 | **不采用** |
| (c) 写回前（PUT 载荷构造） | `MetadataApplyPlanner.tagsForPut()` / `MetadataAdapters.put()` | 能覆盖整份集合，包含未参与抓取的既有标签；一处收口 | 破坏半径最大，需要全部防护 | **采用**（对存量归一） |
| (d) 服务器插件 / 服务器标签规则 | LRR tagrules + `rewrite_tags` | 所有客户端共用，最彻底 | `[事实]` 批量重写既有档案只有 WebSocket 入口（`Controller/Batch.pm:151-177`），**无 REST**；APP 只能出说明书 | 作为可选兜底，不依赖 |

### 4.2 如何避免"每次抓取都重写全库标签"

`[事实]` `MetadataRepository.applyPending` 在 `plan.diff.hasEffectiveChanges == false && unappliedPatch == MetadataPatch()` 时会走 `noEffectiveChange` 分支：**不发 PUT**，直接标 SAVED（`MetadataRepository.kt:213-241`）。
`[推断]` 因此只要满足两个条件，"重复抓取不重复写"是**自然结果**，不需要额外状态表：
1. 归一结果**幂等**（同一英文 → 同一中文，与词库版本绑定）；
2. 已归一的档案里不再存在"可归一的英文标签"。

**实现要点（伪代码，约 12 行）**：

```
归一(档案标签集 T, 词库快照 D 的版本 v) -> (T', 报告)
  out = []
  for tag in T:
     (ns, val) = split(tag)
     if ns ∉ 白名单:            out += tag;  continue   // 机器命名空间/未知命名空间不动
     if val 已是中文:            out += tag;  continue   // 幂等：中文值不再查表
     hit = D.lookup(canonical(ns), val)                // 词典：英 → 中
     if hit == null:            out += tag; 报告.未命中 += tag; continue
     out += "${canonical(ns)}:${hit}"
  去重(out)   // 见 4.4 的冲突规则
  return (out, 报告)
```

关键点：**判据是"值是否为中文"，不是"是否查过词典"**。这样归一后的标签集在下一次运行时是**不动点**。

**例外与边界**：
- `[事实]` 词库更新会改变映射（EHT 修订译名）。所以"已归一"必须绑定 `dataVersion`。否则一次词库升级会让全库进入"待重新归一"。→ **建议：归一映射版本锁定**，词库升级后只提示"有 N 条映射可用新译名更新"，不自动重写。
- `[事实]` 服务器 PUT 的 `tags` 为空串等于"清空全部标签"（`tags=""` 仍 `defined`，`Model/Archive.pm:311-313` → `set_tags($id,"")`）。所以任何 `T'` 为空或大幅缩水的计算都必须 **abort**（见第 6 节）。

### 4.3 幂等性

- **同一档案重复归一**：靠 4.2 的"不动点"性质 + `NO_EFFECTIVE_CHANGES` 早退。✅
- **同一批次重复执行**：`[事实]` PUT 是覆盖式，第二次执行算出相同集合 → 服务端 `uniq` 后集合相同 → 服务器侧数据不变。幂等。✅
- **大小写漂移**：`[事实]` 服务端索引键小写、存储串保留原大小写（`Utils/Database.pm:550,570`）；APP identity 小写（`CanonicalTag.kt:26-32`）但在 PUT 时发送 `raw`（`MetadataAdapters.kt:37`）。→ 归一后的中文值没有大小写，问题自动消失。✅
- **NFC vs NFKC 漂移（真实风险）**：`[事实]` 服务器只做 NFC（`Utils/Redis.pm:21`）；APP 的 `CanonicalTag.normalize` 做 **NFKC** + 空白折叠（`CanonicalTag.kt:54-57`）。→ 全角字符、连续空格、兼容字符（`①`、`／`、全角数字）在 APP 看来同一个标签，在服务器看来是两个。归一时**必须用服务端原样串做比较，不要用 NFKC 后的键去判等**，否则可能误合并。⚠️

### 4.4 用户手动添加的标签

`[事实]` 手工编辑标签会把 `MetadataFieldName.TAGS` 加入 `userOverrides`（`MetadataWorkbenchSheet.kt:88-102`），此后 provider patch 的全部标签操作会被转成 `unapplied` + CONFLICT（`MetadataPatchMerger.kt:191-209`），并触发 `UNAPPLIED_PATCH_REVIEW_REQUIRED` 阻断（`MetadataApplyPlanner.kt:75-77`）。
`[事实]` `MetadataRepository.withClientAnnotations:556` 让 `userOverrides` **只增不减**，无法通过刷新清除。
`[事实]` `[推断]` 现有所有权粒度是**整字段**（TAGS 全有或全无），没有"单标签 pin"。

**建议**：
1. 首期**尊重现状**：`userOverrides` 含 TAGS 的档案**默认不参与自动归一**（与现有保护一致，不引入新机制）。
2. 需要覆盖时，走**显式单本确认**，并在向导里标注"该档案标签由用户编辑过"。
3. 中期把所有权细化到**单标签 pin**（`CanonicalTag.source == USER` 已经在 `MetadataPatchMerger.kt:243-245` 被用作"用户标签不可被自动删除"，是现成的抓手）。这属于 **后续改动**，不在本期。

### 4.5 归一失败 / 冲突（同名中文对应多个英文）

`[事实]` 词典是**单向**（英 → 中）。归一方向（英 → 中）的冲突形态是**多对一**：两个不同英文 key 得到同一中文译名。
`[推断]` 现实案例：`female:glasses` 与 `male:glasses` 都译作「眼镜」——但它们的命名空间不同，归一后仍是 `female:眼镜` 与 `male:眼镜`，可区分。真正撞车的是**同命名空间内**两条不同英文映射到同一中文。

**建议的冲突分类与处理**：

| 情形 | 处理 |
|---|---|
| 归一目标中文在**本档案内已由另一条标签占用** | **视为真冲突**：默认两条都保留（归一那条退化为保留英文原形），并在预览中标红，要求人工选择「合并 / 保留两条 / 跳过」 |
| 归一目标中文在**本档案内已存在且来源是用户手写** | 视为**同义归一**（这正是想要的效果）：合并为一条，采用中文写法 |
| 归一目标为空 / 词库无条目 | 保留英文原形，记入"未命中"统计（与现有 `WritebackScreen` 行为一致） |
| 归一目标含 `,` 或其他无法安全表达的字符 | **拒绝该条**（保留原形），记入"无法安全表达"（见 6.3） |

### 4.6 预览 / 回滚 / 分批

**预览**（现有 `WritebackScreen` 已有 scan → preview → execute 四步骨架，缺的是）：
1. 逐条展示 `原标签 → 目标标签`，并显示**数量变化**（归一可能把 N 条压成 M 条，必须显式提示）；
2. 单列「冲突」「未命中」「无法安全表达」三张清单，默认不勾选；
3. **导出预览为 JSON**（见回滚），可在离线状态下复核。

**回滚（建议两层）**：
1. **批次快照表**（新增 Room 表，属于后续改动）：批次 id、arcid、`beforeTags`（服务端原样串）、`afterTags`、归一映射版本、时间戳。撤销 = 逐本把 `beforeTags` 原样 PUT 回去。
2. **整库备份**：执行前调用 `queueBackup()` + `downloadBackup()`，把 JSON 落到应用私有目录（`LanraragiRepository.kt:765-787` 已具备）。作为"批次快照也坏了"的兜底。

> 强烈建议**先做 (1)**：整库恢复会覆盖备份之后的一切改动，而批次快照只回滚本次动过的档案。

**分批（性能）**：
`[事实]` 现有实现每本 2 次请求（GET + PUT）+ 500ms 间隔（`WritebackScreen.kt:445, 467`）。全库 N 本 ≈ `N × (RTT×2 + 500ms)`。
`[推断]` 建议：
- 扫描阶段用 `/api/search` 分页（现有做法）拿到 arcid 全集与重量级信息；
- 执行阶段仍**逐本 GET**（保证不覆盖别人的并发改动，这是 `MetadataRepository` 的既有原则），但：
  - 支持**暂停/续跑**，游标持久化（不要每次从头）；
  - 进度与失败重试用现有服务器任务区（`JobTracker`）呈现；
  - 每本写后校验（复用 `matchesPutPayload` 思路），失败即停并报告，不盲目继续。

---

## 5. 联想与搜索的一致性

### 5.1 联想的现状

`[事实]` 联想候选是**两路合并**：词库（英文 canonical）+ 服务器热门标签（实际形态），见 `LibraryScreen.kt:1679-1685`。
`[事实]` 去重键是 `(namespace, tagKey)`，所以 `artist:foo`（词库形）与 `艺术家:foo`（服务器形）**不会互相去重**——两形并列出现。
`[事实]` 点选后填入的是**词库形**（英文），并且会加 `$`（`SearchQueryCodec.exactTag`）。
`[推断]` 于是现状是：**联想就地把用户引到"服务器上不存在的那一轨"**，用户点了候选却搜不到，且因为标题模糊检索兜底，他不会看到空结果，只会看到一批不相关的档案。这是当前体验最差的一环。

### 5.2 建议的候选呈现

对每个候选，区分三种来源与三种可填形态：

| 候选来源 | 展示 | 点选后填入 |
|---|---|---|
| 服务器实况（weight > 0） | 主标题=服务器写法（着色），副标题=词典译名（若有） | **服务器写法**（保证命中） |
| 仅词库（服务器不存在） | 主标题=词典译名，副标题=英文原 key，带"库内不存在"标记 | 归一目标形态（中文），并在填入后给出"库内暂无此标签"提示 |
| 归一冲突 | 标红，展开显示候选目标 | 要求用户在展开项里选一个 |

**排序建议**：`库内存在 > 词库存在`；同级内沿用 `TagSuggestionRanker` 的 `TagMatchQuality` 与词频（`:48` 的 `ln` 加权）。
**去重建议**：以**归一后的目标形态**为主键去重（而不是 `(namespace, tagKey)`），这样 `artist:foo` 与 `artist:巴` 合并为一个候选，副标题同时展示两种写法。

### 5.3 「服务器只有中文写法，点选英文候选后应该发生什么」

`[事实]` 词典是英→中单向，所以「英文候选 → 中文库内写法」这条路是**通的**；反向（中文 → 英文）需要建逆索引，而词典本身就有（`translateName` 字段），只是当前 `NormalizationIndex.translated` 只用它做"中文输入 → 英文 tag"。

**建议行为**：
1. 点选英文候选时，先查"该英文 key 的归一目标中文"是否在服务器实况集合中；
2. 在 → 填入**中文写法**，候选上即时显示"→ artist:巴"的替换提示（可撤销）；
3. 不在（词库有但库内没有，或库内仍是英文）→ 填入**库内实际写法**；若库内两者皆无，填入中文并提示"库内暂无此标签，记得回写归一"。
4. 无论哪种情况，都要在**提交前**给出可见的"你输入的是 X，实际查询的是 Y"提示——因为服务端无 OR，用户没有第二次机会。

### 5.4 搜索条件的一致化（没有 OR 怎么办）

`[事实]` 服务端条件之间是 AND，**没有 OR**（`Model/Search.pm:334`）。
`[事实]` "客户端发两次请求做伪 OR"在分页语义下**不可行**：`/api/search` 只返回 `start` 起的一页与 `recordsFiltered`，两次请求的并集需要全量拉回本地，且 `hidecompleted`、`sortby`、计数都要在客户端重做 → **不建议实现伪 OR**。

**建议的三段式**：

| 阶段 | 客户端行为 |
|---|---|
| 归一前（库内双轨） | 提交前把每个 token 过一遍归一映射（把 `TagTranslationStore.normalizeSearchQuery` 接上线，它已经写好了）。**只能单向（英→中）**：英文输入自动折到中文轨，中文输入不变。若该 token 在服务器实况里**同时存在中英两形**，结果区顶部提示"该条件库内存在两种写法，结果可能不完整" |
| 归一前（值为中英异形，无法单向折算） | **提示 + 一键拆成两次搜索**（用户点一下切到"另一轨"），不做自动 OR |
| 归一后 | 映射退化为恒等；`normalizeSearchQuery` 保留即可（对历史搜索词仍有用） |

`[事实]` 现有 `SearchQueryCodec.kt:28-33` 的校验只覆盖语法层面，**没有**"该条件在库内是否可达"的检查。`[推断]` 建议补"可达性检查"：与 `/api/database/stats` 缓存（`SearchDiscoveryRepository` 的 30 分钟 TTL，`:28`）比对，对不可达 token 给出具体原因（含保留符号 / 命名空间不存在 / 只有另一轨）。

### 5.5 排序的一致性（容易被忽略）

`[事实]` `LibraryQuery.kt:55-58` 的 `LibrarySort` wire 值是写死的英文 `artist/language/series/date_added/lastread/title`。
`[事实]` 服务端排序在原串上做**大小写敏感**的 `qr/$sortkey/`（`Model/Search.pm:576,595`），无该命名空间一律记 `"zzzz"` 排到最后。
`[推断]` **只要服务器上命名空间是中文，按作者/系列/语言排序就会静默退化为"全部排到最后"**（用户看到的是"排序没反应"）。这是"命名空间必须统一为英文"的最强论据（见 3.6）。

---

## 6. 风险与防护

### 6.1 为什么这是破坏性操作

`[事实]` 为什么是破坏性操作：PUT 契约原文写着 overwrite（`openapi.yaml:1038`），`set_tags` 覆盖旧值（`Utils/Database.pm:460-486`，`$append` 默认 0）；APP 主链路发送的是**完整标签集**（`MetadataApplyPlanner.kt:104-125`）；归档层无版本回退，`tag_provenance` 只记来源不记历史值。

### 6.2 必须的防护措施

1. **写前逐本重读**（现有 `MetadataRepository.applyPending` 已做；`WritebackScreen` 也做了 `getMetadata`，但**没做写后校验**）。→ 归一路径必须补写后校验。
2. **写后校验**：复用 `matchesPutPayload` 的指纹思路（`MetadataRepository.kt:565-582`），服务端返回的标签集与预期不一致 → 立即停批并报告。
3. **熔断阈值**（当前**完全没有**）：单本标签数从 N → M，若 `M == 0` → **无条件 abort**（空串 = 清空全部标签）；若 `M < N × 0.6` → 默认 abort，需显式二次确认；全批次若 abort 次数 ≥ 3 → 自动停止整批。
4. **命名空间白名单**：只对 `parody/character/group/artist/female/male/mixed/language/reclass/location/other` **及其对应中文写法**生效。`source`/`date_added`/`timestamp`/`uploader`/`category`/`temp` **绝不参与**。
5. **不可删除**：词库未命中的英文标签、`TagSource.USER` 标签、`source:` 标签一律保留原形。
6. **必须先备份**：执行前 `queueBackup()` + `downloadBackup()`，把 JSON 落到应用私有目录；备份失败 → **不执行**。
7. **UI 必须明示不可逆代价**：归一后服务器上不再有英文原形，英文精确搜索将完全依赖客户端映射；词典缺条目时英文/中文联想都会失效。
8. **并发**：服务端写操作有 `exec_with_lock("archive-write:$id")`（`Api/Archive.pm:346-348`），单档案不会写坏；但**同一档案的两次逻辑覆盖**（别人的网页端编辑 vs 本批次）是最后写入者胜。→ 写前重读 + 写后校验是唯一可行的一致性策略，不能靠锁。

### 6.3 LANraragi 侧的字符转义与「无法安全表达」的标签

| 字符/形态 | 服务端行为 | 依据 | 影响 |
|---|---|---|---|
| 半角逗号 `,` | 存储层按 `,` 切分 → **一个标签变成两条** | `Utils/Tags.pm:41`、`Utils/Database.pm:536-537` | **归一产生的译名若含半角逗号，必须拒绝**（全角「，」安全） |
| 首尾空白 | 被 `trim` 掉 | `Utils/Tags.pm:43-44` | 标签不能靠前后空格区分 |
| 引号 `"` | 触发引号精确语法（`Model/Search.pm:418-421`） | 查询侧 | 客户端已拒绝填入（`SearchQueryCodec.kt:40`） |
| 尾部 `$` | 被当作"精确"标记吃掉（`Model/Search.pm:441-446`） | 查询侧 | **以 `$` 结尾的标签无法用 `$` 语法精确表达**；需改用双引号包裹 |
| `_` | 查询侧变成 `?`（单字符通配） | `Model/Search.pm:459` | 只能**过度匹配**，不会漏；`date_added` 这类标签实际仍能命中 |
| `%` | 查询侧变成 `*` | `Model/Search.pm:462` | 同上 |
| `*` | 保持通配符 | `Model/Search.pm:461` 附近（未转义） | 客户端已拒绝填入 |
| `[` `]` `^` `\` | 查询侧前面加 `\` 转义 | `Model/Search.pm:455` | **可安全表达**，但客户端 `SearchQueryCodec.exactTag` 未拒绝也未特殊处理；本地 `matches()` 用 `Regex.escape`（`SearchQueryCodec.kt:75`）与之一致 |
| 大小写 | 索引键小写、存储串原样 | `Utils/Database.pm:550,570` | 查询大小写不敏感；归一后中文值无此问题 |
| 组合字符 | 仅 NFC | `Utils/Redis.pm:21` | APP 的 NFKC 判等与服务端 NFC 判等**不等价**，可能误合并 |

**明确的「不要自动写回」清单**：

1. 未取得最近一次全库备份，或备份下载失败。
2. 计算结果与"原集合"的差异**超出预设阈值**（尤其：结果为空、或减少超过 40%）。
3. 服务器 `/api/info` 报出 `excluded_namespaces`（默认 `source, date_added`，`Model/Config.pm:221`）与本次归一命名空间集合相交 —— 用户已经选择隐藏这些命名空间，说明他不希望它们出现在主流程。
4. 该档案 `userOverrides` 含 `MetadataFieldName.TAGS` 且用户未显式勾选"包含手工标签"。
5. 该档案存在未提交的 `pendingPatch`（`MetadataState.hasPendingPatch`）。
6. 服务器实况读取失败 / 解析出的标签数为 0 / 响应结构异常。
7. 词库快照缺失、版本低于本次归一映射记录的 `dataVersion`。
8. 词库版本已变更但用户没有重新预览（映射漂移）。
9. 目标标签命中 6.3 表格中标为"必须拒绝"的字符。
10. 服务器被多人共用时，无人确认这是一次全局性变更（**本稿无法从代码判断该服务器是否共享，需用户拍板，见 8-Q11**）。

### 6.4 现有 D6 向导的具体缺口（如果要复用它）

`[事实]` 缺口清单：
1. **绕过 `MetadataRepository`**（`WritebackScreen.kt:410` 直连 `updateArchiveMetadata`）：没有 plan/diff/写后校验/重基检查，也没有登记 `userOverrides` → 与工作台链路的状态可能不一致。
2. **没有回滚、没有熔断、没有覆盖率上限**：不存 before 快照；`merged` 为空时照发 PUT（`:409-415`），而服务端 `tags=""` 会清空全部标签；`selected` 默认**全选**所有可翻译词条（`:272-276`）。
3. **不能处理中文命名空间**（`analyzeEnglishTag` 的 ASCII 判据，`:488`）。
4. **判等口径与主链路不同**：`:385-401` 用自建 `parseIdentity`（NFKC + 小写）而非复用 `CanonicalTag`/`MetadataPatchMerger`，存在两套判等逻辑长期漂移的风险。

---

## 7. 分期路线

> 每阶段的"最小可验证产物"都设计成**可观测行为/可断言输出**，不依赖新增独立文件（受本任务约束）。凡涉及改动源码的，均标注为后续实施项。

### 阶段 0：只读诊断（零写）

- 做法：拉 `/api/database/stats`（`minweight=1`）+ 抽样 `/api/metadata`，按 canonical 分数归类命名空间，输出每个语义的「中文写法 weight / 英文写法 weight / 疑似等价对」，把第 2 节的对照表变成**可复算的实况报告**。
- **最小可验证产物**：(1) 一份数字报告，能与用户给出的 1788/422/394/… 对得上；(2) 断言"同一语义存在两套命名空间"的纯函数单测（输入 `TagStat` 列表，输出等价对）。
- **不做**：任何 PUT。

### 阶段 1：只读一致化（零写，客户端身份归一）

- 内容：(1) 把服务器实况的中文命名空间登记进 `TagNamespaceRegistry`（`labelZh` + `aliases`，如 `group` 增 `团队`、`artist` 增 `艺术家`、`reclass` 增 `重新分类`），让 `canonicalNamespace` 与 `TagQueryParser` 立刻认识它们；(2) 建立**双向等价索引**（英 key ↔ 中译名）供显示去重与搜索归一使用；(3) 把 `TagTranslationStore.normalizeSearchQuery` **接上线**（现成的，目前是死代码）；(4) `groupTags`/chip：同一档案内等价的两条标签合并为一个 chip，副文本标注"库内另有英文写法"。
- **最小可验证产物**：(1) 搜索 `作者:xxx` 与 `artist:xxx` 得到**同一批结果**；(2) 含 `艺术家:X` 与 `artist:X`（值同形或可映射）的档案只出一个 chip；(3) 排序面板里「按作者排序」不再静默失效（或明确提示不支持）。
- **风险**：阶段 1 会让 `canonicalNamespace` 把「艺术家」折成「artist」，从而改变写回时的命名空间 → 必须确认写回链路仍发送 `raw`（`MetadataAdapters.kt:37`，安全），并在阶段 1 期间**禁用 D6 向导**。

### 阶段 2：可预览 + 可导出 + 可回滚骨架（仍零写服务器）

- 内容：归一规划器（纯函数）+ 冲突/未命中/无法表达三张清单 + 批次快照表 + 预览导出 JSON，把"归一会发生什么"变成可审计的产物。
- **最小可验证产物**：(1) 全库扫描后导出 JSON，含每本 `before → after` 与差异原因；(2) 熔断规则的单测（空集 abort、缩水 > 40% abort、含半角逗号拒绝）；(3) **端到端干跑**：完整走一遍扫描→预览→导出，服务器零 PUT（可用写权限被拒/断点断言验证）。
- **不做**：任何 PUT。

### 阶段 3：单本写回（默认关闭）

- 内容：复用 `MetadataRepository` 的 plan/apply 边界（**不要**继续在 UI 层直连 `LanraragiRepository.updateMetadata`）；只归一白名单命名空间；跳过 pin 标签；写后指纹校验；批次快照落库。
- **最小可验证产物**：单本归一成功；写后校验通过；**撤销按钮能把该本还原为 before 原样串**（逐字相同）。门槛：必须先备份成功。

### 阶段 4：分类 / 小批量写回

- 入口复用现有「范围：分类/全库」选择器（`WritebackScreen.kt:75-79`）。
- **最小可验证产物**：一个分类（建议先挑 10–50 本的小分类）全量归一；中断后可续跑；失败项可单独重试；整批撤销可用。门槛：备份 + 熔断 + 逐本校验齐备。

### 阶段 5：全库批量 + 服务器侧兜底

- 内容：全库分批、暂停/续跑、进度与失败在服务器任务区呈现。
- 并行建议：**服务器标签规则**（LRR `tagrules` + `enable_tagrules`）让服务器插件刮回来的英文在入库时就落地为中文，减少 APP 侧归一压力。`[事实]` 该配置**只能由用户在服务器端手动维护**（无 REST 写入端点）；规则语法支持 `replace` 与 `hash_replace`，且 `rewrite_tags` 只作用于**插件刮削结果**（`Model/Plugins.pm:275-279`）与 WebSocket 批量页。`[推断]` 字典条目数以万计，用规则表覆盖全量映射不现实 → 更现实的是**只配少量高频规则**（如命名空间级 `replace_ns`），值级归一留给 APP。
- **最小可验证产物**：全库归一完成后重跑阶段 0 的报告，确认"同一语义双轨"归零（或只剩白名单外的残留）。

---

## 8. 需要用户拍板的问题

> 每题给出 2–3 个选项与推荐。推荐优先置于首项。

**Q1（值形态）归一后，服务器上的标签**值**应该是什么形态？**
- A（推荐）**中文译名**（`artist:巴`）。与既有规划 `docs/enhancement-plan-2026-09-13.md:103` 一致；显示层零成本；与 EHT 体系一致。代价：英文原形消失，英文精确搜索完全依赖客户端映射。
- B 保留英文原形（`artist:foo`），只统一命名空间。搜索侧零风险，但对中文用户的可读性没有改善，且与既有规划冲突。
- C 不做值归一，只建立客户端等价表（形态 C）。最安全，但"写回服务器实现归一"的目标落空。

**Q2（命名空间形态）命名空间应该统一成哪一套？**
- A（推荐）**英文 canonical**（`artist:` 取代 `艺术家:`、`group:` 取代 `团队:`）。依据：服务端 `sort_results` 大小写敏感匹配命名空间（`Model/Search.pm:576,595`），APP 排序 wire 值写死英文（`LibraryQuery.kt:55-58`）；不统一则「按作者/系列排序」静默失效。
- B 统一为**服务器现有的中文写法**（`艺术家:`）。显示最直观，但排序、服务端标签规则、EHT 词库 key 全部对不上。
- C 命名空间不动，只归一值。改动最小，但双轨保留，搜索分裂问题只解决一半。

**Q3（registry 用词冲突）`group`=社团/团队、`artist`=作者/艺术家、`reclass`=重分类/重新分类，以谁为准？**
- A（推荐）**以服务器实况为准**，把 `团队`/`艺术家`/`重新分类` 写进 registry 的 `aliases`（并调整 `labelZh`）。理由：这三个写法已经在用户库里有 222/422/5 条真实标签，registry 的说法属于 APP 自造。
- B 以 registry 为准，把服务器改成 `社团`/`作者`/`重分类`。会把用户已有的中文写法也换掉，破坏面更大。
- C 三处单独做成可配置项。灵活但增加状态。

**Q4（归一范围）归一作用于哪些标签？**
- A（推荐）**白名单**：`parody/character/group/artist/female/male/mixed/language/reclass/location/other` 及其中文对应写法。
- B 所有非机器命名空间（排除 `source/date_added/timestamp/temp/uploader/category`）。
- C 全部标签，包括机器命名空间。强烈不建议。

**Q5（未命中）词库没有条目的英文标签怎么办？**
- A（推荐）**保留英文原形**（与现有 D6 行为一致，`WritebackScreen.kt:922-931`）。
- B 删除，交给下次刮削重来。属于静默数据删除，不建议。
- C 归入 `other:<原值>`。会破坏语义。

**Q6（用户标签）用户手动添加/编辑过的标签是否参与归一？**
- A（推荐）**不参与**，尊重现有 `userOverrides` 保护（`MetadataPatchMerger.kt:191-209`）。零新增机制。
- B 参与，但必须先逐条勾选确认。
- C 完全参与，绕过保护。会破坏现有所有权语义，不建议。
- `[补充]` 若希望细粒度控制，需要把所有权从"整字段"细化到"单标签 pin"——这是独立改动，建议单独立项。

**Q7（英文别名）归一后，英文原形要不要在服务器上留一份？**
- A（推荐）**不留**，服务器纯单轨（真正意义的归一）。
- B 同时保留英文别名标签（`artist:foo` 与 `artist:巴` 并存）。搜索最兼容，但库更大、双轨问题只解决一半、统计分裂。
- C 不落库，只保留在 APP 的双向映射表里（= 形态 C 的过渡态）。安全，但迁移期会很长。

**Q8（写回闸门）首期允许的写回粒度？**
- A（推荐）**仅单本手动**，写后校验 + 可撤销。
- B 分类批量（先挑小分类）。
- C 全库批量。风险最高，必须等 A/B 验证过。

**Q9（回滚策略）回滚怎么做？**
- A（推荐）**批次 before/after 快照 + 单本/整批撤销**（新增 Room 表），辅以执行前全库备份。
- B 只做执行前全库备份 + 用 `restoreBackup` 整库恢复。会覆盖备份之后的一切其它改动，不建议作为主方案。
- C 两者都做。最稳但工作量翻倍。

**Q10（词典漂移）词库更新后，已归一的标签怎么办？**
- A（推荐）**锁定归一映射版本**，升级后只提示"有 N 条可用新译名更新"，需重新预览才写。
- B 跟随最新词库，下次扫描时自动用新译名重写。会在用户不知情时改动已有数据。
- C 手工维护一份固定映射表，不随词库变化。

**Q11（命名空间白名单外的双轨）`语言 12` 但未见 `language`；`日期/时间戳` 这类中文机器命名空间要不要处理？**
- A（推荐）**不动机器命名空间**（`date_added`/`timestamp`/`source`），只处理语义命名空间。
- B 把中文机器命名空间也归一到英文。会让服务端 `sort_results` 的 `date_added`/`timestamp` 排序恢复，但风险更高。
- C 全部保留并隐藏。现状即可。
- `[说明]` 这题也顺带确认：**该服务器是否为多人共用**？若是共享库，任何"全库归一"都应由服务器管理员承担，而非某个客户端单方面执行。

**Q12（搜索侧对残留的处置）归一完成前，遇到"库内有另一轨写法"的条件怎么处理？**
- A（推荐）**客户端单向归一（英→中）+ 明确提示**；值为中英异形且无法折算时，提供"一键切到另一轨再搜一次"。
- B 客户端发两次请求做伪 OR。评估结论：与 `/api/search` 分页语义冲突，需把结果集全量拉回本地并在客户端重做排序/计数，不建议。
- C 不做处理，只在帮助文案里说明。体验最差。

**Q13（D6 向导的定位）现有的「元数据中文化向导」怎么处置？**
- A（推荐）**收编**：改为经过 `MetadataRepository` 的 plan/apply 边界，补齐写后校验、熔断、批次快照，并扩展为支持命名空间归一（当前只做值、且值必须是 ASCII）。
- B 保留现状，只补熔断与回滚。会留下"两套写回链路、两套判等逻辑"的长期漂移风险。
- C 删除，另起一套。浪费已有的扫描/预览骨架。

---

## 9. 决策落定（2026-09-15 第二轮交互确认）

用户已就下面 6 项作出选择。未列入的 Q9/Q10/Q11/Q12 留到对应阶段再定，但 Q9/Q10 的推荐项
已被 Q3「首期零写服务器」吸收（首期根本不写，回滚与词典漂移不成为问题）。

| 编号 | 决策 | 用户选择 |
| --- | --- | --- |
| Q1/Q2 | 归一目标形态 | **英文命名空间 + 保留中文值**（`artist:巴`）。服务端 `sortby` 按字面匹配命名空间，只有这样排序才能生效；显示层零成本，因为值本来就是中文 |
| Q3 | 命名空间词表以谁为准 | **以 EhTagTranslation 的 `rows` 表为准**（`artist↔艺术家`、`group↔团队`、`reclass↔重新分类`）；registry 里 `作者/社团/重分类` 这三处是 APP 自造，随之修正 |
| Q8 | 首期写回闸门 | **只做只读诊断 + 预览导出，完全不写服务器** |
| Q6 | 用户手动标签 | **不参与归一**，沿用现有 `userOverrides` 保护，不新增机制 |
| Q13 | 旧「元数据中文化向导」 | **收编**进统一写回链路（补写后校验、熔断、批次快照；并扩展到命名空间归一） |
| Q11 附加 | 库的使用范围 | **我一个人用**，可以按「全库归一」设计，不需要额外的降级开关 |

### 9.1 由此确定的执行路径

**阶段 0 · 词表与映射（零写服务器，可独立验证）**

1. 解析 `rows` 得到 `namespace ↔ 中文写法` 双向表，作为唯一权威来源；`TagNamespaceRegistry` 的
   `labelZh` 与 `aliases` 以它为准修正（`group=团队`、`artist=艺术家`、`reclass=重新分类`）。
   注意 `rows` 当前被解析器排除（见实施记录第三轮），需要新增一条**单独读取**的通道，
   而不是把它重新混进标签词库。
2. 「展示用命名空间」与「服务端原文命名空间」拆成两个字段。现有实现有一处真实陷阱：
   `SearchDiscoveryRepository.normalize` 会把 `TagStat.namespace` 写成 `canonicalNamespace(...)`，
   一旦 `canonicalNamespace("艺术家")` 开始返回 `artist`，热门标签就会被改写成服务器上
   不存在的写法。**这一步必须在改 registry 之前完成**。

**阶段 1 · 只读诊断（零写服务器）**

3. 对全库跑一遍：每条标签判「已是英文命名空间 / 可归一 / 词库无对应条目 / 值含保留字符」，
   产出可导出的报告（数量、样例、逐条前后对照）。
4. 输出「归一后会变成什么」的完整预览，包含冲突项（同名中文值对应多个英文 key）与无法表达的项。

**阶段 2 · 客户端消费（零写服务器）**

5. 联想：命中 `rows` 表时同时给出中文写法与英文写法的候选，标来源与频次；点选英文候选时，
   若服务器只有中文写法，明确提示而不是静默生成一条搜不到的条件。
6. 排序：`LibrarySortResolver` 从「提示不生效」升级为「解析到服务器实际使用的命名空间」，
   发出去的 `sortby` 用服务器真的有的那个。

**阶段 3 · 单本写回（需要新的写回链路，本阶段才第一次写服务器）**

7. 收编 `WritebackScreen`：走 `MetadataRepository` 的 plan/apply，补写后校验
   （`matchesPutPayload`）、熔断（`merged` 为空时**绝不**发 PUT，服务端 `tags=""` 等于清空全部标签）、
   批次 before/after 快照与撤销。
8. 值必须是中文、且**不再要求 ASCII**（现有 `analyzeEnglishTag` 因为要求全 ASCII，
   连 `艺术家:せぶんがー` 都跳过）。

**阶段 4 · 小批量 → 全库**

9. 先按分类小批量；验证通过后再全库。服务器为自用，不需要额外的多人降级开关，但仍要求
   执行前全库备份。

### 9.2 与首期同样重要的一条

`Q12` 的结论仍然成立：**没有 OR**，`artist:x` 与 `艺术家:x` 无法一次查全。因此归一的价值不只是
「好看」，而是把「同一个语义的两套写法」收敛成一套，从根上消掉这个分裂；在归一完成前，
客户端只能如实提示用户库里到底有哪一套。


---
name: jimble-upgrade
description: jimble の版を上げるとき・古い版（1.x など）のコードを直すときに使う。手元の版と変更の見方、migrate-2 と jimbleCheck、非推奨（[removal]）の置き換え先、jimbleSkills で skill を揃える（分けた skill の追加・古い skill の片付け・手で直した skill の扱い）、版ごとに要る手当ての引き先を含む。
---

# jimble の版を上げる

全体の決まりと、ほかの skill への案内は `jimble`。

**版で挙動が変わることがある。**手元の版は `jimble version`、
変更は <https://github.com/hidemikimura/jimble/blob/main/CHANGELOG.md> を見る（版ごとの「上げるときに要ること」もここにある）。

## 1.x から上げる

<https://jimble.io/ja/migrate-2.md> を読み、`./gradlew jimbleCheck` を流す。
`isError()` や `DBTransaction`、`if (!DBUtil.load(...))`、`request().getString(...)`、`cookies().put(cookie)`、`router.path("/x")`、
`long id = db.insert(...)`、`Data row = db.select(...)` はコンパイルエラーになるので、そこは迷わない。
2.2.2 以前から上げるときは、古い Cookie の署名が 2.5.2 から既定で読まれない（`jimble-web-session`）。

## 非推奨（`[removal]` の警告）は書き換える

新しく書くコードで使わない。

| 非推奨 | 置き換え |
| --- | --- |
| `selectOrThrow` / `selectListOrThrow` | `select` / `selectList`（失敗はもともと例外） |
| `insertNoReturnKey` | `insert`（1件の INSERT なので件数は 1） |
| `DB.isBatchSuccess` / `RedisLockStatus.Failed` | 要らない（失敗は例外） |
| `Convertor` / `IConvertor` / `PropertyUtil` | `Converter`（`jimble-json`） |
| `Cookies.sign(値)` | `Cookies.sign(名前, 値)`（`jimble-web-session`） |

## skill を揃える（jimbleSkills）

```bash
./gradlew jimbleSkills               # この版の skill に揃える
./gradlew jimbleSkills --overwrite   # 手で直したもの・控えの無いものも入れ替える（消す）
```

- `.claude/jimble-skills.properties` に、jimble が置いた skill のハッシュを控えている（手で直さない）
- **この版で増えた skill は足す。**jimble が置いたままの skill は入れ替える
- **この版で無くなった skill は、jimble が置いたままなら消す**（2.7.8。skill を分けた・名前を変えたときに古いものが残らない）
- **手で直した skill・控えの無い skill は触らない**（無くなったものも消さない）。出力に「触らない」「古い（手で直してある）」と出る
- **アプリ固有の決まりは skill を直さず、プロジェクトの根の `AGENTS.md` に書く。**書き足しを `AGENTS.md` へ移してから `--overwrite` で揃える
- 揃えたら `.claude/skills/` と `.claude/jimble-skills.properties` をコミットする

## 版ごとの手当て（よく踏むもの）

- **2.7.11 からは Maven Central に無い。**置き場は `https://repo.jimble.io`。`settings.gradle.kts` の `pluginManagement` と `dependencyResolutionManagement`（か `build.gradle.kts` の `repositories`）の両方に、`mavenCentral()` より前に `maven("https://repo.jimble.io")` を足す。足さないと `Plugin [id: 'io.jimble.db', version: '<上げた版>'] was not found` や `Could not find io.jimble:jimble-web:<上げた版>` で止まる。座標とプラグイン ID は変わらない
- **2.7.17 からパスワードのハッシュの既定が Argon2id。**保存済みの BCrypt はそのまま照合できるが、新しいハッシュは 97 文字前後（暗号化すると 150 文字ほど）になる。
  パスワードの列が BCrypt の 60 文字に合わせてあれば、上げる前に広げる（広げられないうちは `hash.password.algorithm = "bcrypt"`）。
  ログインに成功したときに `PasswordUtil.needsRehash` を見て作り直すと、順に Argon2id に入れ替わる（`jimble-web-auth`）
- **3.0.0 からビルダーの SELECT 句の列に `AS 表__列` が付かない**（結果は位置で表名の入れ子にするので、`db.select(builder)` の結果の形は同じ）。
  `builder.sql()` の文字列を `db.select(String)` に渡している・外側の SQL で `表__列` を参照しているなら、ビルダーのまま渡すか `sql.select_alias = true`。
  結果の列の読み方を SQL ごとに覚えるので、DDL はアプリのマイグレーションで流す（当てたら忘れる）。アプリの外で DDL を流したら起動し直す（`jimble-db-migration`）
- **3.0.0 でロック・SQL 結果キャッシュ・流量制限のキーのハッシュが XXH3 に変わった。2.7.19 以前と同時に動かさない**（全台を止めてから上げる。並ぶと `DBLock` の排他が効かない）。
  上げたら `DBLock.create(db, キー)` を呼び直す（起動のたびに呼ぶ書き方なら不要）。SQL 結果キャッシュは 1 回全部外れ（`sql_cache.ttl = 0s` なら古いものが残るので消す）、流量制限は数え直しになる
- codegen の出力が変わった版（2.2.4・2.5.4）は、上げたあと `codegenCheck` が差分を出したら `codegen` で作り直してコミットする（`jimble-db-migration`）
- jooby_base から移した `batch_master` や MQ の表を使うなら 2.5.9 以上（`jimble-batch-run`）
- `router.ignore_case` と `router.redirect_to_canonical` を両方使うなら 2.5.11 以上（`jimble-web`）
- 2 引数・3 引数の `Oidc.callback` は 2.5.2 以上（`jimble-web-auth-methods`）
- `jimble new` の雛形は 2.5.2 でアクセスログを `AsyncAppender` に替えた。前の版で作ったアプリは書き換える（`log.md`）

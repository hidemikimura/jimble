# AI への案内

このリポジトリは **jimble**（Java 25 の Web フレームワーク）で書いたアプリです。
**注釈も DI も無い**ので、Spring の書き方（`@RestController` / `@Autowired` / `@Transactional` など）は通りません。

## 書く前に

- **`.claude/skills/jimble/SKILL.md` を読む。**DB は `jimble-db`、Web は `jimble-web`、バッチと MQ は `jimble-batch` の skill
- **推測で書かない。**全ページが Markdown で取れる：目次 <https://jimble.io/ja/llms.txt>、個別は `https://jimble.io/ja/<名前>.md`
- 使っている jimble の版は `build.gradle.kts` の `io.jimble` の版。版で挙動が変わることがあるので、
  変更は <https://github.com/hidemikimura/jimble/blob/main/CHANGELOG.md> を見る

## 書いたあとに

- **`./gradlew jimbleCheck`** で、jimble の既知の落とし穴を機械的に確かめる（見つかったものには直し方と引き先が付く）
- jimble の版を上げたら **`./gradlew jimbleSkills`** で skill をその版に揃える
  （手で直した skill は上書きしない。アプリ固有のことは skill を直さず、下の「このアプリの決まり」に書く）

## このアプリの決まり

（アプリ固有の決まりはここに書き足す）

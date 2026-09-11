# セキュリティ / Security

（English follows Japanese.）

## 脆弱性を見つけたら

**公開の Issue を立てないでください。**

このリポジトリの **Security → Report a vulnerability** から、非公開で報告してください。

<https://github.com/hidemikimura/jimble/security/advisories/new>

GitHub の非公開報告（private vulnerability reporting）を使っています。
やりとりも修正も公表も、そのスレッドの中で完結します。
**メールの窓口は用意していません**（受け口を1つにして、見落としを作らないためです）。

書いていただけると助かるもの。

- どの版で起きるか（`0.3.0` など）
- どのモジュールか（`jimble-web` など）
- 再現の手順、できれば最小のコード
- 何ができてしまうか（読めてはいけないものが読める、など）

## 受け取ったあと

- **数日以内に、受け取ったことをお返しします。**返事が無い場合は届いていない可能性があるので、
  同じスレッドで一声かけてください
- **修正の期限は約束していません。**個人で開発しているためです。ただし、
  <b>影響の大きいものから手を付けます</b>
- 直った版を公開してから、GitHub Security Advisory として公表します
- **報告してくださった方のお名前を載せます**（不要とおっしゃる場合は載せません）

## どの版が直るか

**最新の版に修正を入れます。**古い版へのバックポートはしません。

まだ 0.x なので、上げていただくのがいちばん早い道です。
公開済みの版は [Maven Central](https://repo.maven.apache.org/maven2/io/jimble/) にあります。

## 対象の範囲

**対象になるもの** — Maven Central に公開している `io.jimble` のライブラリと Gradle プラグイン。

**対象にならないもの。**

- **`examples/` のサンプルアプリ。**説明のためのもので、本番に置くようには作っていません。
  接続情報も鍵も既定値のままです（`jimble` / `jimble` など）。<b>そのまま動かさないでください</b>
- **ドキュメントサイト**（jimble.io）の見た目や内容
- **依存ライブラリそのものの脆弱性。**Dependency graph と Dependabot で追いかけています（要件 NF-S-07）。
  ただし<b>「jimble の使い方のせいで、その脆弱性に手が届いてしまう」</b>のであれば、それは対象です
- **設定でそうすると決めたもの。**たとえば `server.trust_proxy = true` にすれば
  `X-Forwarded-For` を信じます（既定は `false`）。<b>そう書いてある動きは脆弱性ではありません</b>——
  ただし<b>ドキュメントと実装が食い違っている</b>なら、それは対象です

---

# Security

## Reporting a vulnerability

**Please do not open a public issue.**

Report it privately through **Security → Report a vulnerability** on this repository:

<https://github.com/hidemikimura/jimble/security/advisories/new>

We use GitHub's private vulnerability reporting. The discussion, the fix and the
publication all happen in that thread. **There is no email address** — one intake
means nothing gets lost in a second inbox.

Helpful things to include:

- the version it happens on (`0.3.0`, say)
- the module (`jimble-web`, say)
- how to reproduce it, ideally as a minimal piece of code
- what it lets someone do (read something they should not, say)

## What happens next

- **We will confirm receipt within a few days.** If you hear nothing, it may not have
  arrived — please say so in the same thread.
- **We do not promise a deadline for the fix.** This is a one-person project. We do
  work through them **worst impact first**.
- We publish a GitHub Security Advisory once the fixed version is out.
- **We credit reporters by name**, unless you would rather we did not.

## Which versions get fixed

**Fixes go into the latest release.** We do not backport to older versions.

This is still 0.x, so upgrading is the fastest path. Released versions are on
[Maven Central](https://repo.maven.apache.org/maven2/io/jimble/).

## Scope

**In scope** — the `io.jimble` libraries and Gradle plugins published to Maven Central.

**Out of scope:**

- **The sample applications under `examples/`.** They exist to explain things, not to be
  deployed. Their credentials and keys are left at defaults (`jimble` / `jimble` and so
  on). **Do not run them as they are.**
- The documentation site (jimble.io) — its content and appearance.
- **Vulnerabilities in dependencies themselves.** Those are tracked through the
  Dependency graph and Dependabot (requirement NF-S-07). If **the way jimble uses a
  dependency is what makes the vulnerability reachable**, that is in scope.
- **Behaviour the configuration asks for.** Set `server.trust_proxy = true` and
  `X-Forwarded-For` is trusted (the default is `false`). **Documented behaviour is not a
  vulnerability** — but **a mismatch between the documentation and the implementation is.**

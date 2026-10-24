// JSON ライブラリとの性能比較（jimble 本体のビルドには入れない）
rootProject.name = "bench-json-compare"

/*
 * -Pjimble.local=true のときは、Maven Central の公開版ではなく
 * このリポジトリの jimble-util をソースから使う（Dson を作り直す前と後を比べるため）。
 * 依存の io.jimble:jimble-util は、取り込んだビルドの :jimble-util に自動で置き換わる。
 */
if (providers.gradleProperty("jimble.local").orNull == "true") {
	includeBuild("../..")
}

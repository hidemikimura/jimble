// SQL の組み立てと設定の読み出しの CPU を測る（jimble 本体のビルドには入れない）
rootProject.name = "bench-sql-cpu"

/*
 * -Pjimble.local=true のときは、Maven Central の公開版ではなく
 * このリポジトリの jimble-db / jimble-web をソースから使う（直す前と後を比べるため）。
 */
if (providers.gradleProperty("jimble.local").orNull == "true") {
	includeBuild("../..")
}

plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")
	id("jimble.publish-conventions")
}

description = "jimble の共通ユーティリティ。Data / 型変換 / JSON / 文字列 / 日時 / IO / HTTP クライアントなど"

/*
 * jooby_base から移送したコード（168ファイル / 約 23,700 行）。
 *
 * 移送時点は警告が多く、種別ごと（unchecked / rawtypes / fallthrough / deprecation /
 * dangling-doc-comments / this-escape / cast / overloads）落としていた（要件 D-15）。
 * <b>2026-09-08 に 81 件を潰して、この抑止をやめた。</b>
 * doclint も同時に有効へ戻した（45 件）。
 * 消せないものは、消せない理由を書いた @SuppressWarnings をその場所に付けてある。
 */

dependencies {
	api(project(":jimble-core"))

	// Data を API に露出する型
	api(libs.jspecify)

	// 設定（HOCON）とログ
	api(libs.typesafe.config)
	api(libs.slf4j.api)
	// エンコーダの実装で logback の型を使う
	compileOnly(libs.logback.classic)
	runtimeOnly(libs.logback.classic)
	// エンコーダのテストで logback のイベントを組み立てる
	testImplementation(libs.logback.classic)

	implementation(libs.tika.core)
	implementation(libs.tika.parser.text)
	implementation(libs.fastcsv)
	implementation(libs.jts.core)
	implementation(libs.caffeine)
	implementation(libs.jbcrypt)
	implementation(libs.brotli.dec)
}

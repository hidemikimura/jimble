/*
 * ビルドの規約を置く「含まれるビルド」（設計書 D-13）。
 *
 * <b>buildSrc ではなく includeBuild にしてある。</b>理由は2つ。
 *
 *   1. gradle-plugin（別ビルド）からも同じ規約を使える。
 *      buildSrc は自分のビルドの中でしか見えないので、
 *      POM の必須項目を2か所に置き続けることになる
 *   2. 規約を直したときに、本体ビルドが丸ごと作り直しにならない
 *
 * すでに gradle-plugin を includeBuild で取り込んでいるので、形も揃う。
 */
rootProject.name = "build-logic"

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}
}

plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")
	id("jimble.publish-conventions")
}

description = "jimble の JSON。JDK だけで書いた読み書き（ゆるい文法・上限つき）"

/*
 * <b>依存は持たない。</b>jimble のほかのモジュールにも、外のライブラリにも依存しない。
 * Data（jimble-util）を作るのは、jimble-util が読み方の設定（作る Map の型）を渡して行う。
 * こうしておくと、JSON だけを使いたいアプリはこの jar だけで足りる。
 */

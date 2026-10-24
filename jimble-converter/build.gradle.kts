plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")
	id("jimble.publish-conventions")
}

description = "jimble の型変換。JDK だけで書いた、Map・Bean・record・コレクション・日時のあいだの変換"

/*
 * <b>依存は持たない。</b>jimble のほかのモジュールにも（jimble-json にも）、外のライブラリにも依存しない。
 * Data（jimble-util）への変換は、jimble-util が作る Map の型を渡して行う。
 */

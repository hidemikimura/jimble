package approval.forms;

import db.approval_forms_example.table_data.request.AbstractRequestData;

/**
 * 申請の型付きアクセサ（要件 F-G-02 / F-D-22）
 *
 * <p>
 * <b>生成された抽象クラスを閉じるだけの1行。</b>
 * {@code AbstractRequestData<T extends AbstractRequestData<?>>} は
 * 自分の型を返し続けるための再帰ジェネリクスになっているので、
 * アプリ側でこう閉じると {@code new RequestData().kind("travel").amount(1000)} が繋がる。
 * </p>
 *
 * <h2>ここに置く理由</h2>
 * <p>
 * <b>{@code db/} の下には置けない。</b>あそこは {@code codegen} のたびに
 * <b>まるごと作り直される</b>ので、手で書いたものは次の生成で黙って消える。
 * </p>
 *
 * <h2>なぜ Data を直に使わないのか</h2>
 * <p>
 * {@code data.getString("kind")} と書くと、<b>列名を打ち間違えても動いてしまう</b>
 * （空が返るだけ）。{@code kind()} ならコンパイルで止まる。
 * マイグレーションで列名を変えたときに<b>どこを直せばいいかが機械に分かる</b>のが本題である。
 * </p>
 */
public class RequestData extends AbstractRequestData<RequestData> {
}

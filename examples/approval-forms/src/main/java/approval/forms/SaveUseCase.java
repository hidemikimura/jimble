package approval.forms;

import db.approval_forms_example.ApprovalFormsExample;
import db.approval_forms_example.table.attachment.Attachment;
import db.approval_forms_example.table.request.Request;
import db.approval_forms_example.table.request_item.RequestItem;

import io.jimble.core.executor.AbstractExecutor;
import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.sql.SQL;
import io.jimble.util.convertor.UploadFile;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 申請を保存する（{@link SaveValidation} を通ったものだけ来る）
 *
 * <p>
 * <b>ここには入力の確認が1行も無い。</b>それが {@link SaveValidation} を
 * 別の Executor にしてある理由である。
 * </p>
 */
public class SaveUseCase extends AbstractExecutor<WebContext> {

	/** 添付の置き場所 */
	static final String UPLOAD_DIR = "build/uploads";

	/** 受け付ける添付の拡張子 */
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".png", ".jpg", ".pdf");

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute (WebContext context) throws Exception {

		Data request = context.request().bodyAll();

		/*
		 * <b>型付きアクセサに詰め替える</b>（要件 F-G-02 / F-D-22）。
		 * 列名を打ち間違えたらコンパイルで止まる。
		 */
		RequestData data = new RequestData()
			.kind(text(request, "kind"))
			.amount(request.getLong("amount"))
			.note(text(request, "note"))
			.status(request.getBoolean(RequestRules.KEY_SUBMIT)
				? RequestRules.STATUS_PENDING
				: RequestRules.STATUS_DRAFT)
			.createdAt(new Date());

		if (!text(request, "needed_on").isEmpty()) {
			data.neededOn(request.getDate("needed_on"));
		}

		long id = save(data, request.getDataList("items"), context);

		context.response().code(201).json("id", id);

	}

	/**
	 * 文字列を取る（無ければ空）
	 *
	 * <p>
	 * <b>{@code getString} は、キーが無いと {@code null} を返す。</b>
	 * そのまま {@code .isEmpty()} を呼ぶと落ちる——下書きは項目が
	 * <b>送られてこない</b>ので、ここは必ず通る道である。
	 * </p>
	 *
	 * <p>
	 * {@code getStringOptional} は空を返してくれるが、
	 * <b>ついでにそのキーを入れてしまう</b>（読んだだけのつもりが増えている）。
	 * リクエストは読むだけにしたいので、ここでは自分で見る。
	 * </p>
	 *
	 * @param data	データ
	 * @param key	キー
	 * @return	値。無ければ空
	 */
	private static String text (Data data, String key) {

		String value = data.getString(key);

		return value == null ? "" : value;

	}

	/**
	 * 申請と明細と添付を、まとめて1つの取引で入れる
	 *
	 * @param data		申請
	 * @param items		明細（null 可）
	 * @param context	コンテキスト
	 * @return	申請ID
	 */
	private long save (RequestData data, List<Data> items, WebContext context) throws Exception {

		/*
		 * <b>途中で落ちたら1件も残さない。</b>申請だけ入って明細が入っていない状態は、
		 * 画面から直しようがない
		 */
		DB db = ApprovalFormsExample.db();

		try (DBTransaction transaction = new DBTransaction(db)) {

			transaction.beginTransaction();

			long id = db.insert(
				SQL.insert(Request.instance())
					.value(Request.kind, data.kind())
					.value(Request.amount, data.amount())
					.value(Request.needed_on, data.containsNeededOn() ? data.neededOn() : null)
					.value(Request.note, data.note())
					.value(Request.status, data.status())
					.value(Request.created_at, data.createdAt()));

			insertItems(db, id, items);
			insertAttachment(db, id, context);

			transaction.commitEndTransaction();

			return id;

		}

	}

	/**
	 * 明細を入れる
	 *
	 * @param db	DB
	 * @param id	申請ID
	 * @param items	明細
	 */
	private static void insertItems (DB db, long id, List<Data> items) {

		if (items == null) {
			return;
		}

		int sortNo = 0;

		for (Data item : items) {

			db.insert(
				SQL.insert(RequestItem.instance())
					.value(RequestItem.request_id, id)
					.value(RequestItem.name, item.getString("name"))
					.value(RequestItem.amount, item.getLong("amount"))
					.value(RequestItem.sort_no, sortNo));

			sortNo++;

		}

	}

	/**
	 * 添付を保存する（要件 F-W-06）
	 *
	 * @param db		DB
	 * @param id		申請ID
	 * @param context	コンテキスト
	 */
	private static void insertAttachment (DB db, long id, WebContext context) {

		UploadFile uploadFile = firstFile(context);

		if (uploadFile == null || uploadFile.fileSize() <= 0) {
			return;
		}

		String extension = extensionOf(uploadFile.fileName());

		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new HttpException(400, "添付は %s だけです".formatted(String.join(" / ", ALLOWED_EXTENSIONS)));
		}

		/*
		 * <b>送られてきた名前をそのまま使わない。</b>
		 * {@code ../../etc/passwd} のような名前が来る。拡張子だけ見て、名前は自分で付ける
		 */
		String saved = UUID.randomUUID() + extension;

		try {

			Path dir = Path.of(UPLOAD_DIR);
			Files.createDirectories(dir);

			/*
			 * <b>ここで写す。</b>一時ファイルはリクエストが終わると消えるので、
			 * パスだけ DB に入れると、あとで無いファイルを掴む
			 */
			Files.copy(uploadFile.file().toPath(), dir.resolve(saved), StandardCopyOption.REPLACE_EXISTING);

		} catch (IOException ex) {
			throw new HttpException(500, "添付を保存できませんでした", ex);
		}

		db.insert(
			SQL.insert(Attachment.instance())
				.value(Attachment.request_id, id)
				.value(Attachment.file_name, saved)
				.value(Attachment.content_type, uploadFile.contentType())
				.value(Attachment.bytes, uploadFile.fileSize())
				.value(Attachment.created_at, new Date()));

	}

	/**
	 * 最初の添付を1つ取る
	 *
	 * <p>
	 * <b>1件でも List にくるまれている。</b>そのまま {@link UploadFile} に
	 * キャストすると落ちる。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @return	無ければ null
	 */
	private static UploadFile firstFile (WebContext context) {

		for (Object value : context.request().bodyFile().values()) {

			if (value instanceof List<?> list) {

				for (Object item : list) {
					if (item instanceof UploadFile uploadFile) {
						return uploadFile;
					}
				}

				continue;

			}

			if (value instanceof UploadFile uploadFile) {
				return uploadFile;
			}

		}

		return null;

	}

	/**
	 * 拡張子を小文字で取る
	 *
	 * @param fileName	ファイル名
	 * @return	拡張子（{@code .png}）。無ければ空
	 */
	private static String extensionOf (String fileName) {

		if (fileName == null) {
			return "";
		}

		int dot = fileName.lastIndexOf('.');

		return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);

	}

}

# 初期データ（N-3 の 5.2）。
#
# 締切バッチが拾うものが要るので、needed_on をばらけさせてある。
# 基準日は「流した日」なので、いつ流しても同じ関係になる。

# --- !Ups

/*
 * 20 件。
 *
 * - 締切が3日以内で pending      … 締切バッチが拾う（6件）
 * - 締切がもっと先で pending     … まだ拾わない（6件）
 * - approved / rejected          … 拾わない（8件。うち古いものは書庫入れの対象）
 *
 * 日付の足し引きは PostgreSQL の書き方である（このサンプルは PostgreSQL 単独）。
 */
insert into request (staff_id, amount, needed_on, status, created_at) values
	  (1,  12000, current_date + 1,   'pending',  now() - interval '2 days')
	, (1,  35000, current_date + 1,   'pending',  now() - interval '2 days')
	, (2,   4800, current_date + 2,   'pending',  now() - interval '3 days')
	, (2, 120000, current_date + 2,   'pending',  now() - interval '3 days')
	, (3,   9800, current_date + 3,   'pending',  now() - interval '1 days')
	, (3,  76000, current_date + 3,   'pending',  now() - interval '1 days')

	, (1,   3200, current_date + 10,  'pending',  now())
	, (1,  58000, current_date + 14,  'pending',  now())
	, (2,  21000, current_date + 20,  'pending',  now())
	, (2,   7400, current_date + 30,  'pending',  now())
	, (3, 150000, current_date + 45,  'pending',  now())
	, (3,   6600, current_date + 60,  'pending',  now())

	, (1,  18000, current_date - 400, 'approved', now() - interval '400 days')
	, (1,  24000, current_date - 380, 'approved', now() - interval '380 days')
	, (2,  31000, current_date - 365, 'approved', now() - interval '365 days')
	, (2,   5200, current_date - 200, 'rejected', now() - interval '200 days')
	, (3,  47000, current_date - 120, 'approved', now() - interval '120 days')
	, (3,   8800, current_date - 90,  'rejected', now() - interval '90 days')
	, (1,  63000, current_date - 30,  'approved', now() - interval '30 days')
	, (2,  11500, current_date - 10,  'approved', now() - interval '10 days')
;

# --- !Downs

delete from request;

# 初期データ（N-3 の 5.2）。
#
# 申請5・レート3。承認とキャッシュの題材ぶんだけ。

# --- !Ups

insert into request (staff_id, amount, status, created_at) values
	  (1,  12000, 'pending',  now())
	, (1,  35000, 'pending',  now())
	, (2,   4800, 'pending',  now())
	, (2, 120000, 'pending',  now())
	, (3,   9800, 'approved', now())
;

-- 端数のあるものを混ぜてある。コードマイグレーションが 10 の倍数に丸める
insert into rate (code, value, updated_at) values
	  ('USD', 151, now())
	, ('EUR', 165, now())
	, ('GBP', 190, now())
;

# --- !Downs

delete from rate;
delete from request;

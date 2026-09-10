# ログインできる人を入れる（N-3 の 5.2「初期データはマイグレーションの1本として入れる」）。
#
# パスワードは3人とも同じ approval-sample である。
# <b>サンプルなので隠さない。</b>隠すと誰も動かせない。
#
#   member1 / approval-sample   申請する人
#   member2 / approval-sample   申請する人
#   approver1 / approval-sample 承認する人（/approvals が見える）
#
# 値は BCrypt のハッシュに pepper（hash.password.pepper）を足したものである。
# 同じパスワードでも3つとも違う文字列なのは、BCrypt が毎回別の塩を使うためである。
# pepper を application.conf から変えると、この3人はログインできなくなる。

# --- !Ups

insert into staff (login_id, password_hash, name, role, created_at) values
	('member1'
		, '$2a$10$YfvIcnLpqqrbIdrEUZ5Fpeh9iyVID7pKc0pnEWPRxL6WIq4y0X3BWapproval-auth-sample-pepper'
		, '申請 太郎', 'member', now())
	, ('member2'
		, '$2a$10$QFc7ye56Tq2.Qt7imDGbye1DCxGXupr/Eji6GN4VYP6kq4KxwNKimapproval-auth-sample-pepper'
		, '申請 花子', 'member', now())
	, ('approver1'
		, '$2a$10$pxsq4PACmlfIIZkqI.Nvgey6xamrpafiZ9/04byp86EOIHg4NtviCapproval-auth-sample-pepper'
		, '承認 一郎', 'approver', now());

# --- !Downs

delete from staff where login_id in ('member1', 'member2', 'approver1');

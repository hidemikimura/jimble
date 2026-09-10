# 一覧の題材になるデータ（N-3 の 5.2）。
#
# 申請 60 件を入れる。<b>データが無いと、ページングも集計も何も見えない。</b>
# 件数と分布は結合テストが当てにしているので、変えるならテストも直すこと。
#
#   部署3 × 社員2 = 6人、1人あたり申請10件 = 60件
#   状態は id の剰余で pending / approved / rejected に散らす
#   金額は 1000 円刻みで散らす

# --- !Ups

insert into department (name, created_at) values
	('営業部', now()), ('開発部', now()), ('総務部', now());

insert into staff (department_id, name, created_at)
select d.id, d.name || ' ' || s.n, now()
from department d
	cross join (values (1), (2)) as s(n);

insert into request (staff_id, kind, amount, needed_on, status, created_at)
select
	s.id
	, (array['travel', 'supply', 'book'])[1 + (n % 3)]
	, 1000 * (1 + ((s.id * 7 + n) % 50))
	, date '2026-10-01' + (((s.id + n) % 30) || ' days')::interval
	, (array['pending', 'approved', 'rejected'])[1 + ((s.id + n) % 3)]
	, now() - (n || ' hours')::interval
from staff s
	cross join generate_series(0, 9) as n;

# --- !Downs

delete from request;
delete from staff;
delete from department;

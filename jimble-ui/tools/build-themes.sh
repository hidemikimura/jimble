#!/bin/sh
#
# vendor/ の CSS モジュールを作り直す
#
#   sh tools/build-themes.sh
#
# tailwind-dark テーマのクラスを増やしたときと、Bootstrap を上げたときに実行する。
#
set -e
cd "$(dirname "$0")/.."

echo "--- tailwind-dark"
npx --yes tailwindcss@3 -c tools/tailwind.config.cjs -i tools/tailwind.css -o /tmp/jb-tailwind.css --minify
node tools/css-to-module.mjs /tmp/jb-tailwind.css vendor/tailwind-dark.css.js "Tailwind CSS（tailwind-dark テーマが使うクラスだけを抽出したもの）"

echo "--- bootstrap5"
npm pack bootstrap@5 --pack-destination /tmp >/dev/null
tar -xzf /tmp/bootstrap-5*.tgz -C /tmp
node tools/css-to-module.mjs /tmp/package/dist/css/bootstrap.min.css vendor/bootstrap5.css.js "Bootstrap 5 の CSS（bootstrap5 テーマが使う）"

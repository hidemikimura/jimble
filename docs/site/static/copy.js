/*
 * コードブロックにコピーのボタンを付ける。
 *
 * 生成時に付けないのは、JavaScript が動かない環境（や印刷）で
 * 押せないボタンだけが残るからである。
 */
document.querySelectorAll('pre.code').forEach(function (block) {

	var button = document.createElement('button');
	button.type = 'button';
	button.className = 'copy';
	button.textContent = 'コピー';
	button.setAttribute('aria-label', 'コードをコピー');

	button.addEventListener('click', function () {

		var code = block.querySelector('code');
		var text = code ? code.innerText : '';

		if (!navigator.clipboard) {
			button.textContent = 'コピーできません';
			return;
		}

		navigator.clipboard.writeText(text).then(function () {
			button.textContent = 'コピーしました';
			setTimeout(function () { button.textContent = 'コピー'; }, 1500);
		}, function () {
			button.textContent = 'コピーできません';
			setTimeout(function () { button.textContent = 'コピー'; }, 1500);
		});

	});

	block.appendChild(button);

});

"""Builds web/privacy.html from docs/privacy-notice.md.

Run from the repo root:  python web/build_privacy.py
Needs Python-Markdown:   pip install markdown
"""
import sys, markdown, re
src, out = "docs/privacy-notice.md", "web/privacy.html"
md = open(src, encoding='utf-8').read()
body = md.split('**Effective date:**', 1)[1].split('## NPC registration', 1)[0].rstrip().rstrip('-').rstrip()
body = '**Effective date:**' + body
body = body.replace(' (see [SECURITY.md](../SECURITY.md))', '')
body = body.replace('(§8)', '([§8](#8-deleting-your-account-and-data))')
html = markdown.markdown(body, extensions=['tables', 'toc'])
page = f'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Privacy notice · Ulap: Mood Diary</title>
<!-- Generated from docs/privacy-notice.md. Edit that file, then run python web/build_privacy.py. -->
<link rel="stylesheet" href="style.css">
</head>
<body>
<main>
<p class="brand">Ulap: Mood Diary</p>
<h1>Privacy notice</h1>
{html}
<p class="links"><a href="delete-account.html">Delete your account</a></p>
</main>
</body>
</html>
'''
open(out, 'w', encoding='utf-8', newline='\n').write(page)

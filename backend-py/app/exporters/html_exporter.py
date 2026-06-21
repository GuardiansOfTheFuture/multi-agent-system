from .base import Exporter
import markdown as md


class HtmlExporter(Exporter):
    def export(self, markdown_text: str, title: str) -> bytes:
        body = md.markdown(markdown_text, extensions=["tables", "fenced_code"])
        html = f"""<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>{title}</title>
<style>body{{font-family:serif;max-width:780px;margin:0 auto;padding:40px;line-height:1.8;color:#333}}
h1,h2,h3{{color:#222}}pre{{background:#f5f5f5;padding:16px;overflow-x:auto}}
code{{background:#f0f0f0;padding:2px 6px;border-radius:3px}}
table{{border-collapse:collapse;width:100%}}th,td{{border:1px solid #ddd;padding:8px;text-align:left}}</style>
</head><body><h1>{title}</h1>{body}</body></html>"""
        return html.encode("utf-8")

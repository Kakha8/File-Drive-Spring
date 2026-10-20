from io import BytesIO
from pathlib import Path

from pypdf import PdfReader, PdfWriter
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont


SOURCE = Path(r"C:\CVs\2026\quantori\KKudava_Software_Engineering.pdf")
OUTPUT = Path(r"C:\git-repos\File-Drive-Spring\output\pdf\KKudava_Software_Engineering_Georgian_Phone.pdf")
FONT_FILE = Path(r"C:\Windows\Fonts\arial.ttf")
NEW_PHONE = "+995 598 92 02 65"


def main() -> None:
    reader = PdfReader(str(SOURCE))
    page = reader.pages[0]
    width = float(page.mediabox.width)
    height = float(page.mediabox.height)

    pdfmetrics.registerFont(TTFont("Arial", str(FONT_FILE)))

    overlay_buffer = BytesIO()
    overlay = canvas.Canvas(overlay_buffer, pagesize=(width, height))
    # Cover only the old number. Coordinates were measured from the source PDF.
    overlay.setFillColorRGB(1, 1, 1)
    overlay.rect(208.5, height - 114.0, 100.0, 13.0, fill=1, stroke=0)
    overlay.setFillColorRGB(0, 0, 0)
    overlay.setFont("Arial", 9.9975)
    overlay.drawString(211.289, height - 112.999, NEW_PHONE)
    overlay.save()

    overlay_buffer.seek(0)
    page.merge_page(PdfReader(overlay_buffer).pages[0])
    writer = PdfWriter()
    writer.add_page(page)
    if reader.metadata:
        writer.add_metadata({k: str(v) for k, v in reader.metadata.items() if v is not None})
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("wb") as stream:
        writer.write(stream)
    print(OUTPUT)


if __name__ == "__main__":
    main()

package com.example.deviceinfo;

import android.content.Context;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.pdf.PrintedPdfDocument;
import android.graphics.pdf.PdfDocument;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;

import java.io.FileOutputStream;
import java.io.IOException;

public class TestPrintDocumentAdapter extends PrintDocumentAdapter {

    private Context context;
    private int pageHeight;
    private int pageWidth;
    private PdfDocument document;
    private int totalPages = 1;

    public TestPrintDocumentAdapter(Context context) {
        this.context = context;
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                         CancellationSignal cancellationSignal,
                         LayoutResultCallback callback, Bundle extras) {
        document = new PrintedPdfDocument(context, newAttributes);
        pageHeight = newAttributes.getMediaSize().getHeightMils() / 1000 * 72;
        pageWidth = newAttributes.getMediaSize().getWidthMils() / 1000 * 72;

        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }

        PrintDocumentInfo info = new PrintDocumentInfo
                .Builder("test_print.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(totalPages)
                .build();
        callback.onLayoutFinished(info, true);
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                        CancellationSignal cancellationSignal,
                        WriteResultCallback callback) {
        for (int i = 0; i < totalPages; i++) {
            if (pageInRange(pages, i)) {
                PdfDocument.PageInfo newPage = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i).create();
                PdfDocument.Page page = document.startPage(newPage);

                if (cancellationSignal.isCanceled()) {
                    callback.onWriteCancelled();
                    document.close();
                    document = null;
                    return;
                }
                drawPage(page);
                document.finishPage(page);
            }
        }

        try {
            document.writeTo(new FileOutputStream(destination.getFileDescriptor()));
        } catch (IOException e) {
            callback.onWriteFailed(e.toString());
            return;
        } finally {
            document.close();
            document = null;
        }
        callback.onWriteFinished(pages);
    }

    private boolean pageInRange(PageRange[] pageRanges, int page) {
        for (PageRange range : pageRanges) {
            if (page >= range.getStart() && page <= range.getEnd())
                return true;
        }
        return false;
    }

    private void drawPage(PdfDocument.Page page) {
        Canvas canvas = page.getCanvas();
        int topMargin = 120;
        int leftMargin = 90;
        int lineSpacing = 48;

        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setAntiAlias(true);
        paint.setFakeBoldText(true);
        paint.setTextSize(34);
        canvas.drawText("--------------------------------", leftMargin, topMargin, paint);

        paint.setTextSize(38);
        canvas.drawText("Столик №*", leftMargin + 215, topMargin + lineSpacing * 2, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(30);
        canvas.drawText("1. Борщ 1шт/500г", leftMargin, topMargin + lineSpacing * 5, paint);
        canvas.drawText("2. Салат зеленый 1шт/300г", leftMargin, topMargin + lineSpacing * 6, paint);
        canvas.drawText("3. Картофель фри 1шт/100г", leftMargin, topMargin + lineSpacing * 7, paint);
        canvas.drawText("4. Пиво светлое  2шт/0,66мл", leftMargin, topMargin + lineSpacing * 8, paint);

        paint.setFakeBoldText(true);
        paint.setTextSize(34);
        canvas.drawText("--------------------------------", leftMargin, topMargin + lineSpacing * 10, paint);
    }

    @Override
    public void onFinish() {
        super.onFinish();
    }
}

package com.example.deviceinfo.print;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.pdf.PrintedPdfDocument;

import java.io.FileOutputStream;
import java.io.IOException;

public class ReceiptPrintDocumentAdapter extends PrintDocumentAdapter {

    private final Context context;
    private final int tableNumber;
    private final String receiptText;
    private PdfDocument document;
    private int pageWidth;
    private int pageHeight;

    public ReceiptPrintDocumentAdapter(Context context, int tableNumber, String receiptText) {
        this.context = context;
        this.tableNumber = tableNumber;
        this.receiptText = receiptText;
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                         CancellationSignal cancellationSignal, LayoutResultCallback callback,
                         Bundle extras) {
        document = new PrintedPdfDocument(context, newAttributes);
        pageHeight = newAttributes.getMediaSize().getHeightMils() / 1000 * 72;
        pageWidth = newAttributes.getMediaSize().getWidthMils() / 1000 * 72;

        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }

        PrintDocumentInfo info = new PrintDocumentInfo.Builder("receipt_table_" + tableNumber + ".pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build();
        callback.onLayoutFinished(info, true);
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                        CancellationSignal cancellationSignal, WriteResultCallback callback) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        drawReceipt(page.getCanvas());
        document.finishPage(page);

        try {
            document.writeTo(new FileOutputStream(destination.getFileDescriptor()));
            callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
        } catch (IOException e) {
            callback.onWriteFailed(e.getMessage());
        } finally {
            document.close();
            document = null;
        }
    }

    private void drawReceipt(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setAntiAlias(true);
        paint.setTextSize(28);

        String[] lines = receiptText.split("\n");
        int y = 80;
        for (String line : lines) {
            canvas.drawText(line, 60, y, paint);
            y += 40;
        }
    }
}

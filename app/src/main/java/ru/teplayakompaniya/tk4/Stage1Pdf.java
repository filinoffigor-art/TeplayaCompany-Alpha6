package ru.teplayakompaniya.tk4;

import android.app.Activity;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.*;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Native Android print flow includes Save as PDF; no HTML rendering or third-party service. */
public final class Stage1Pdf {
    private Stage1Pdf(){}
    public static void export(Activity activity,String title,String text){
        PrintManager manager=(PrintManager)activity.getSystemService(Activity.PRINT_SERVICE);
        if(manager==null)return;
        manager.print(title,new PrintDocumentAdapter(){
            @Override public void onLayout(PrintAttributes oldAttributes,PrintAttributes attributes,CancellationSignal cancellation,LayoutResultCallback callback,Bundle extras){
                if(cancellation.isCanceled()){callback.onLayoutCancelled();return;}
                callback.onLayoutFinished(new PrintDocumentInfo.Builder(title+".pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),true);
            }
            @Override public void onWrite(PageRange[] ranges,ParcelFileDescriptor destination,CancellationSignal cancellation,WriteResultCallback callback){
                PdfDocument pdf=new PdfDocument();
                try(FileOutputStream output=new FileOutputStream(destination.getFileDescriptor())){
                    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(12);List<String> lines=new ArrayList<>();
                    for(String paragraph:text.split("\\n",-1)){if(paragraph.isEmpty()){lines.add("");continue;}while(!paragraph.isEmpty()){int length=paint.breakText(paragraph,true,515,null);if(length<=0)length=1;lines.add(paragraph.substring(0,length));paragraph=paragraph.substring(length);}}
                    for(int first=0,page=0;first<lines.size();first+=40,page++){
                        if(cancellation.isCanceled()){callback.onWriteCancelled();return;}
                        boolean included=false;for(PageRange range:ranges)if(page>=range.getStart()&&page<=range.getEnd())included=true;if(!included)continue;
                        PdfDocument.Page sheet=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,page+1).create());
                        for(int line=first;line<Math.min(lines.size(),first+40);line++)sheet.getCanvas().drawText(lines.get(line),40,50+(line-first)*18,paint);
                        pdf.finishPage(sheet);
                    }
                    pdf.writeTo(output);callback.onWriteFinished(ranges);
                }catch(Exception error){callback.onWriteFailed("Не удалось создать PDF");}finally{pdf.close();}
            }
        },new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
    }
}

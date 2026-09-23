package ru.teplayakompaniya.tk4;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Resolution-independent native icons: one 24-unit grid for navigation and cards. */
public final class ReferenceIcon extends View {
    private final String name;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    public ReferenceIcon(Context context) { this(context,"home",0xff07844f); }
    public ReferenceIcon(Context context, String name, int color) {
        super(context); this.name=name; paint.setColor(color); paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,paint);}
    private void box(Canvas c,float a,float b,float x,float y,float r){c.drawRoundRect(a,b,x,y,r,r,paint);}
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); c.save();float size=Math.min(getWidth(),getHeight());
        c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);
        paint.setStyle(Paint.Style.STROKE);
        switch(name){
            case "home": case "objects":
                path.rewind();path.moveTo(3,11);path.lineTo(12,3);path.lineTo(21,11);c.drawPath(path,paint);
                line(c,5,10,5,21);line(c,19,10,19,21);line(c,5,21,10,21);line(c,14,21,19,21);
                line(c,10,21,10,14);line(c,14,21,14,14);line(c,10,14,14,14);break;
            case "calendar":
                box(c,3,5,21,21,3);line(c,3,10,21,10);line(c,8,3,8,7);line(c,16,3,16,7);
                line(c,7,14,10,14);line(c,14,14,17,14);line(c,7,18,10,18);break;
            case "bell":
                path.rewind();path.moveTo(5,17);path.lineTo(6,15);path.lineTo(6,10);path.cubicTo(6,2,18,2,18,10);path.lineTo(18,15);path.lineTo(20,18);path.lineTo(4,18);c.drawPath(path,paint);
                c.drawArc(9,18,15,23,0,180,false,paint);line(c,12,2,12,4);break;
            case "profile": case "engineer":
                c.drawCircle(12,7,4,paint);c.drawArc(3,12,21,27,180,180,false,paint);line(c,3,20,21,20);break;
            case "people":
                c.drawCircle(8,7,3,paint);c.drawCircle(17,8,2.5f,paint);
                c.drawArc(2,12,14,26,180,180,false,paint);c.drawArc(12,13,23,26,210,140,false,paint);break;
            case "helmet":
                c.drawArc(4,5,20,23,180,180,false,paint);box(c,2,15,22,19,2);line(c,9,5,9,12);line(c,15,5,15,12);break;
            case "chart":
                paint.setStyle(Paint.Style.FILL);box(c,3,13,7,21,1);box(c,10,8,14,21,1);box(c,17,3,21,21,1);break;
            case "money":
                c.drawOval(4,3,20,9,paint);c.drawArc(4,7,20,13,0,180,false,paint);
                c.drawArc(4,11,20,17,0,180,false,paint);c.drawArc(4,15,20,21,0,180,false,paint);
                line(c,4,6,4,18);line(c,20,6,20,18);break;
            case "wallet":
                box(c,3,5,21,20,3);box(c,14,10,22,16,2);c.drawCircle(17,13,.6f,paint);line(c,5,5,17,2);break;
            case "settings":
                c.drawCircle(12,12,6,paint);c.drawCircle(12,12,2.5f,paint);
                for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);line(c,12,3,12,5);c.restore();}break;
            case "plus": line(c,4,12,20,12);line(c,12,4,12,20);break;
            case "back":line(c,15,5,8,12);line(c,8,12,15,19);break;
            case "arrow":line(c,9,5,16,12);line(c,16,12,9,19);break;
            case "alert":c.drawCircle(12,12,9,paint);line(c,12,6,12,13);c.drawCircle(12,17,.6f,paint);break;
            case "survey":box(c,5,3,20,22,2);line(c,9,7,16,7);line(c,9,12,16,12);line(c,9,17,16,17);break;
            default:c.drawCircle(12,12,8,paint);line(c,8,12,16,12);
        }
        c.restore();
    }
}

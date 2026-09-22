package ru.teplayakompaniya.tk4;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.json.JSONObject;

/** Offline UI fixtures live in the test APK only. Never contact production or mutate Sheets. */
public final class Stage1Smoke extends Instrumentation {
    private MainActivity activity;
    private Throwable failure;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1000);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); // First-install pairing dialog.
            waitForIdleSync();
            check(activity.getSharedPreferences("tk4_connected", 0).getString("stage1_upgrade_sentinel", "").equals("preserved"), "Upgrade lost preferences");
            onUi(() -> {
                invoke("render");
                check(!allText(activity.getWindow().getDecorView()).contains("420 000"), "Legacy demo balance leaked");
                check(allText(activity.getWindow().getDecorView()).contains("Не заполнено"), "Missing data not explicit");
            });
            capture("01-unpaired");
            onUi(() -> {
                JSONObject fixture = new JSONObject("{\"ok\":true,\"serverTime\":\"OFFLINE QA FIXTURE\",\"user\":{\"id\":\"QA\",\"name\":\"Тестовый профиль QA\",\"role\":\"OWNER\",\"finance\":true},\"kpis\":{\"turnover\":120000,\"expenses\":150000,\"profit\":-30000,\"objectsInWork\":0,\"plannedObjects\":0,\"leads\":0,\"surveys\":0,\"contracts\":0,\"averageCheck\":0,\"debt\":0,\"plannedReceipts\":0},\"objects\":[],\"employees\":[],\"surveys\":[],\"income\":[],\"expenses\":[],\"techTasks\":[],\"dayPlans\":[],\"assignments\":[],\"paymentPlan\":[],\"calendar\":[],\"media\":[],\"attention\":[]}");
                fixture.put("accountable",new JSONObject("{\"Игорь\":{\"balance\":-35000,\"expenses\":135000,\"receivedFromCompany\":100000}}"));
                fixture.put("objects",new org.json.JSONArray("[{\"id\":\"QA-OBJECT\",\"address\":\"Тестовый объект QA\",\"client\":\"QA\",\"status\":\"Подтверждён\",\"planStart\":\"2026-09-18\",\"planEnd\":\"2026-09-21\",\"contract\":50000,\"paid\":0}]"));
                Method apply=MainActivity.class.getDeclaredMethod("applyBootstrap",JSONObject.class);apply.setAccessible(true);apply.invoke(activity,fixture);
                field("liveSyncOk",true);field("snapshotPeriod","Месяц");field("currentPeriod","Месяц");invoke("render");
                check(allText(activity.getWindow().getDecorView()).contains("-30"),"Loss was clamped to zero");
            });
            capture("02-home-fixture");
            onUi(()->{
                String text=allText(activity.getWindow().getDecorView());
                check(text.indexOf("Быстрый доступ")<text.indexOf("Ключевые показатели"),"Approved block order changed");
                check(text.contains("Общие расходы")&&text.contains("В работе"),"Approved main cards missing");
                bounds(activity.getWindow().getDecorView(),false);
                clickDescription(activity.getWindow().getDecorView(),"Профиль");
            });
            onUi(()->check(allText(activity.getWindow().getDecorView()).contains("Личные данные"),"Profile entry did not navigate"));
            onUi(()->activity.onBackPressed());
            String[] screens={"objects","object:QA-OBJECT","tech:QA-OBJECT","finance","analytics","calendar","settings","profile","quick","kpi:notifications","installers","engineers","managers","surveys"};
            for(String screen:screens){onUi(()->invoke("navigate",screen));SystemClock.sleep(250);onUi(()->bounds(activity.getWindow().getDecorView(),false));capture(screen.replace(':','-'));onUi(()->activity.onBackPressed());}
            onUi(()->{invoke("navigate","accountable:Игорь");String text=allText(activity.getWindow().getDecorView());check(text.contains("Отрицательный остаток"),"Negative accountable balance treated as missing");check(text.contains("Возместить личные средства"),"Reimbursement entry point missing");});capture("accountable-negative");onUi(()->activity.onBackPressed());
            onUi(()->{invoke("selectPeriod","Год");check(!allText(activity.getWindow().getDecorView()).contains("120 000"),"Old period value leaked");});
            capture("period-missing");
            for(String role:new String[]{"MANAGER","ENGINEER","INSTALLER"})onUi(()->{field("apiRole",role);field("scopedData",false);invoke("navigate","finance");check(!allText(activity.getWindow().getDecorView()).contains("Передать"),"Role access leak: "+role);});
            result.putString("stream","STAGE1_SMOKE_PASSED: navigation, missing data, negative profit, bounds, period, roles, preserved preferences\n");
            finish(-1,result);
        } catch(Throwable error){result.putString("stream","STAGE1_SMOKE_FAILED: "+error+"\n");finish(1,result);}
    }
    private interface Task{void run() throws Exception;}
    private void onUi(Task task) throws Exception{failure=null;runOnMainSync(()->{try{task.run();}catch(Throwable e){failure=e;}});waitForIdleSync();if(failure!=null)throw new Exception(failure);}
    private void invoke(String name) throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
    private void invoke(String name,String value) throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,String.class);m.setAccessible(true);m.invoke(activity,value);}
    private void field(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private boolean clickDescription(View view,String label){
        if(view.isClickable()&&label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())){view.performClick();return true;}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(clickDescription(((ViewGroup)view).getChildAt(i),label))return true;
        return false;
    }
    private String allText(View view){StringBuilder s=new StringBuilder();if(view instanceof TextView)s.append(((TextView)view).getText()).append('\n');if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)s.append(allText(((ViewGroup)view).getChildAt(n)));return s.toString();}
    private void bounds(View view,boolean scrollable){boolean skip=scrollable||view instanceof HorizontalScrollView;if(!skip&&view.isShown()&&view.getWidth()>0){int[] location=new int[2];view.getLocationOnScreen(location);int width=activity.getResources().getDisplayMetrics().widthPixels;check(location[0]>=-2&&location[0]+view.getWidth()<=width+2,"Horizontal overflow: "+view.getClass().getSimpleName());}if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)bounds(((ViewGroup)view).getChildAt(n),skip);}
    private void capture(String name)throws Exception{waitForIdleSync();SystemClock.sleep(250);Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"No screenshot");File directory=new File(getTargetContext().getExternalFilesDir(null),"stage1-qa");directory.mkdirs();try(FileOutputStream output=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}bitmap.recycle();}
}

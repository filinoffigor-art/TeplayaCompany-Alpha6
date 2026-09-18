package ru.teplayakompaniya.tk4;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.time.LocalDate;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** New tasks only: the v2.1 endpoint cannot safely patch existing child IDs. */
final class Stage1TaskEditor {
    private final Activity activity;
    private final LinearLayout content;
    private final ApiClient api;
    private final MainActivity.ObjectItem object;
    private final List<Stage> stages=new ArrayList<>();
    private final List<Payment> payments=new ArrayList<>();
    private final List<Worker> workers=new ArrayList<>();
    private final Runnable refresh;
    private final EditText start,end,note;
    private final CheckBox materials;
    Stage1TaskEditor(Activity activity,LinearLayout content,ApiClient api,MainActivity.ObjectItem object,List<MainActivity.InstallerItem> installers,Runnable refresh){
        this.activity=activity;this.content=content;this.api=api;this.object=object;this.refresh=refresh;
        title(content,"Новое ТЗ · "+object.id);
        start=date(content,"Дата начала",object.planStart);end=date(content,"Дата окончания",object.planEnd);
        materials=new CheckBox(activity);materials.setText("Материалы и ресурсы подготовлены");content.addView(materials);
        title(content,"Монтажники и согласованные суммы");
        for(MainActivity.InstallerItem installer:installers){LinearLayout box=card(content);CheckBox selected=new CheckBox(activity);selected.setText(installer.name+" · "+installer.id);box.addView(selected);EditText amount=field(box,"Согласованная сумма, ₽","",true);workers.add(new Worker(installer.id,installer.name,selected,amount));}
        if(installers.isEmpty())label(content,"Не заполнено: сервер не передал монтажников. Сохранение требует назначения хотя бы одного сотрудника.");
        title(content,"План по этапам / дням");
        label(content,"Для каждого этапа задайте дату и плановый объём. Диапазоны этапов и правки существующих заданий требуют нового серверного контракта.");
        LinearLayout stageList=new LinearLayout(activity);stageList.setOrientation(LinearLayout.VERTICAL);content.addView(stageList);addStage(stageList);
        Button addStage=button("Добавить этап");addStage.setOnClickListener(v->addStage(stageList));content.addView(addStage);
        title(content,"График оплат");label(content,"График покрывает неоплаченный остаток договора: "+Math.max(0,object.contract-object.paid)+" ₽. Уже полученные деньги остаются в истории объекта.");
        LinearLayout paymentList=new LinearLayout(activity);paymentList.setOrientation(LinearLayout.VERTICAL);content.addView(paymentList);if(object.contract>object.paid)addPayment(paymentList);
        Button addPayment=button("Добавить платёж");addPayment.setOnClickListener(v->addPayment(paymentList));content.addView(addPayment);
        note=field(content,"Комментарий монтажникам","",false);
        Button save=button("Сохранить ТЗ в Google Sheets");save.setOnClickListener(v->save(save));content.addView(save);
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private void label(LinearLayout parent,String text){TextView view=new TextView(activity);view.setText(text);view.setTextSize(13);view.setPadding(0,dp(8),0,dp(8));parent.addView(view);}
    private void title(LinearLayout parent,String text){TextView view=new TextView(activity);view.setText(text);view.setTextSize(18);view.setTypeface(null,Typeface.BOLD);view.setPadding(0,dp(18),0,dp(8));parent.addView(view);}
    private LinearLayout card(LinearLayout parent){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);view.setPadding(dp(16),dp(12),dp(16),dp(12));GradientDrawable background=new GradientDrawable();background.setColor(Color.rgb(246,248,251));background.setCornerRadius(dp(22));view.setBackground(background);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(10);parent.addView(view,params);return view;}
    private EditText field(LinearLayout parent,String title,String value,boolean number){label(parent,title);EditText edit=new EditText(activity);edit.setText(value);edit.setMinHeight(dp(48));if(number)edit.setInputType(InputType.TYPE_CLASS_NUMBER);parent.addView(edit);return edit;}
    private EditText date(LinearLayout parent,String title,String value){String formatted=value;try{if(value.contains("."))formatted=LocalDate.parse(value,java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu")).toString();}catch(Exception ignored){}EditText field=field(parent,title,formatted,false);field.setFocusable(false);field.setOnClickListener(v->{LocalDate initial=LocalDate.now();try{initial=LocalDate.parse(field.getText().toString());}catch(Exception ignored){}new DatePickerDialog(activity,(picker,y,m,d)->field.setText(LocalDate.of(y,m+1,d).toString()),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();});return field;}
    private Button button(String text){Button button=new Button(activity);button.setText(text);button.setAllCaps(false);button.setMinHeight(dp(48));return button;}
    private void addStage(LinearLayout list){LinearLayout box=card(list);Stage stage=new Stage(field(box,"Название этапа / задача","",false),date(box,"Дата выполнения",object.planStart),field(box,"Единица измерения","",false),field(box,"Плановый объём","",true));stages.add(stage);Button remove=button("Удалить этап из формы");remove.setOnClickListener(v->{stages.remove(stage);list.removeView(box);});box.addView(remove);}
    private void addPayment(LinearLayout list){LinearLayout box=card(list);Payment payment=new Payment(field(box,"Название платежа","",false),date(box,"Дата платежа",""),field(box,"Сумма, ₽","",true));payments.add(payment);Button remove=button("Удалить платёж из формы");remove.setOnClickListener(v->{payments.remove(payment);list.removeView(box);});box.addView(remove);}
    private String required(EditText field){String value=field.getText().toString().trim();if(value.isEmpty())throw new IllegalArgumentException("Заполните все обязательные поля");return value;}
    private long positive(EditText field){long value=Long.parseLong(required(field));if(value<=0)throw new IllegalArgumentException("Суммы и объёмы должны быть больше нуля");return value;}
    private void save(Button save){
        if(!api.hasToken()){Toast.makeText(activity,"Сначала подключите устройство к API",Toast.LENGTH_LONG).show();return;}
        try{
            LocalDate from=LocalDate.parse(required(start)),to=LocalDate.parse(required(end));if(to.isBefore(from))throw new IllegalArgumentException("Окончание раньше начала");
            JSONObject body=new JSONObject();body.put("objectId",object.id);body.put("engineerId",api.getUserId());body.put("engineer",api.getUserName());body.put("workType",object.workType);body.put("planStart",from.toString());body.put("planEnd",to.toString());body.put("materialsReady",materials.isChecked());body.put("status",materials.isChecked()?"Готово":"Черновик");body.put("comment",note.getText().toString());
            JSONArray assignments=new JSONArray();for(Worker worker:workers)if(worker.selected.isChecked()){JSONObject row=new JSONObject();row.put("installerId",worker.id);row.put("name",worker.name);row.put("agreedAmount",positive(worker.amount));row.put("from",from.toString());row.put("to",to.toString());row.put("status","Назначен");assignments.put(row);}if(assignments.length()==0)throw new IllegalArgumentException("Выберите монтажника и согласованную сумму");body.put("assignments",assignments);
            JSONArray days=new JSONArray();for(Stage stage:stages){LocalDate date=LocalDate.parse(required(stage.date));if(date.isBefore(from)||date.isAfter(to))throw new IllegalArgumentException("Этап выходит за даты объекта");JSONObject row=new JSONObject();row.put("dayNo",java.time.temporal.ChronoUnit.DAYS.between(from,date)+1);row.put("date",date.toString());row.put("task",required(stage.name));row.put("unit",required(stage.unit));row.put("plannedQty",positive(stage.quantity));row.put("status","План");days.put(row);}if(days.length()==0)throw new IllegalArgumentException("Добавьте этап плана");body.put("days",days);
            JSONArray paymentRows=new JSONArray();long total=0;for(Payment payment:payments){long amount=positive(payment.amount);total=Math.addExact(total,amount);JSONObject row=new JSONObject();row.put("stageNo",paymentRows.length()+1);row.put("stageName",required(payment.name));row.put("plannedDate",LocalDate.parse(required(payment.date)).toString());row.put("plannedAmount",amount);paymentRows.put(row);}if(total!=Math.max(0,object.contract-object.paid))throw new IllegalArgumentException("Сумма графика должна совпадать с неоплаченным остатком договора");body.put("payments",paymentRows);
            save.setEnabled(false);save.setText("Сохраняем…");api.mutate("saveTechTask",body,new ApiClient.Callback(){public void onSuccess(JSONObject json){refresh.run();}public void onError(String error){save.setEnabled(true);save.setText("Сохранить ТЗ в Google Sheets");new android.app.AlertDialog.Builder(activity).setMessage(error).setPositiveButton("Закрыть",null).show();}});
        }catch(Exception error){Toast.makeText(activity,error instanceof IllegalArgumentException?error.getMessage():"Проверьте даты и суммы",Toast.LENGTH_LONG).show();}
    }
    private static final class Stage{final EditText name,date,unit,quantity;Stage(EditText name,EditText date,EditText unit,EditText quantity){this.name=name;this.date=date;this.unit=unit;this.quantity=quantity;}}
    private static final class Payment{final EditText name,date,amount;Payment(EditText name,EditText date,EditText amount){this.name=name;this.date=date;this.amount=amount;}}
    private static final class Worker{final String id,name;final CheckBox selected;final EditText amount;Worker(String id,String name,CheckBox selected,EditText amount){this.id=id;this.name=name;this.selected=selected;this.amount=amount;}}
}

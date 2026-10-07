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

/** Stage 1 technical-task editor. Existing children are patched by stable IDs; silent delete is never used. */
final class Stage1TaskEditor {
    private final Activity activity;
    private final LinearLayout content;
    private final ApiClient api;
    private final MainActivity.ObjectItem object;
    private final List<Stage> stages=new ArrayList<>();
    private final List<Payment> payments=new ArrayList<>();
    private final List<Worker> workers=new ArrayList<>();
    private final Runnable refresh;
    private final EditText start,end,note,reason;
    private final CheckBox materials;
    private final boolean editing;
    private String techTaskId="";
    private int techTaskRevision=1;
    private long requiredPaymentTotal=0;

    Stage1TaskEditor(Activity activity,LinearLayout content,ApiClient api,MainActivity.ObjectItem object,List<MainActivity.InstallerItem> installers,Runnable refresh){
        this(activity,content,api,object,installers,null,refresh);
    }

    Stage1TaskEditor(Activity activity,LinearLayout content,ApiClient api,MainActivity.ObjectItem object,List<MainActivity.InstallerItem> installers,JSONObject snapshot,Runnable refresh){
        this.activity=activity;this.content=content;this.api=api;this.object=object;this.refresh=refresh;
        JSONObject saved=findTask(snapshot,object.id);editing=saved!=null;
        if(editing){techTaskId=saved.optString("TechTask_ID",saved.optString("ID"));techTaskRevision=saved.optInt("Revision",1);}
        title(content,(editing?"Редактирование ТЗ · ":"Новое ТЗ · ")+object.id);

        String startValue=editing?saved.optString("План начала",object.planStart):object.planStart;
        String endValue=editing?saved.optString("План окончания",object.planEnd):object.planEnd;
        start=date(content,"Дата начала",startValue);end=date(content,"Дата окончания",endValue);
        materials=new CheckBox(activity);materials.setText("Материалы и ресурсы подготовлены");
        if(editing)materials.setChecked(saved.optBoolean("Материалы готовы",false));
        content.addView(materials);

        if(editing){
            label(content,"Существующие строки редактируются по постоянным ID. Их нельзя удалить снятием галочки: удаление требует отдельной подтверждаемой операции.");
            reason=field(content,"Причина изменения *","",false);
        }else reason=null;

        title(content,"Монтажники и согласованные суммы");
        LinkedHashMap<String,JSONObject> existingAssignments=new LinkedHashMap<>();
        if(editing&&snapshot!=null){
            JSONArray rows=snapshot.optJSONArray("assignments");
            if(rows!=null)for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);if(row==null)continue;
                String task=row.optString("TechTask_ID");
                if(techTaskId.equals(task)&&object.id.equals(row.optString("Object_ID")))existingAssignments.put(row.optString("Installer_ID"),row);
            }
        }
        Set<String> rendered=new HashSet<>();
        for(MainActivity.InstallerItem installer:installers){
            JSONObject existing=existingAssignments.get(installer.id);
            addWorker(installer.id,installer.name,existing);rendered.add(installer.id);
        }
        for(Map.Entry<String,JSONObject> entry:existingAssignments.entrySet()){
            if(rendered.contains(entry.getKey()))continue;
            JSONObject row=entry.getValue();
            String name=row.optString("installerName",row.optString("Монтажник",entry.getKey()));
            addWorker(entry.getKey(),name,row);
        }
        if(workers.isEmpty())label(content,"Не заполнено: сервер не передал монтажников. Сохранение требует назначения хотя бы одного сотрудника.");

        title(content,"План по этапам / дням");
        LinearLayout stageList=new LinearLayout(activity);stageList.setOrientation(LinearLayout.VERTICAL);content.addView(stageList);
        int existingStages=0;
        if(editing&&snapshot!=null){
            JSONArray rows=snapshot.optJSONArray("dayPlans");
            if(rows!=null)for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);if(row!=null&&techTaskId.equals(row.optString("TechTask_ID"))&&object.id.equals(row.optString("Object_ID"))){addStage(stageList,row);existingStages++;}
            }
        }
        if(existingStages==0)addStage(stageList,null);
        Button addStage=button("Добавить этап");addStage.setOnClickListener(v->addStage(stageList,null));content.addView(addStage);

        title(content,"График оплат");
        LinearLayout paymentList=new LinearLayout(activity);paymentList.setOrientation(LinearLayout.VERTICAL);content.addView(paymentList);
        int existingPayments=0;
        if(editing&&snapshot!=null){
            JSONArray rows=snapshot.optJSONArray("paymentPlan");
            if(rows!=null)for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);if(row!=null&&object.id.equals(row.optString("objectId"))&&techTaskId.equals(row.optString("techTaskId"))){
                    addPayment(paymentList,row);requiredPaymentTotal=Math.addExact(requiredPaymentTotal,Math.round(row.optDouble("plannedAmount",0)));existingPayments++;
                }
            }
        }
        if(!editing){
            requiredPaymentTotal=Math.max(0,object.contract-object.paid);
            label(content,"График покрывает неоплаченный остаток договора: "+requiredPaymentTotal+" ₽. Уже полученные деньги остаются в истории объекта.");
            if(requiredPaymentTotal>0)addPayment(paymentList,null);
        }else{
            label(content,"При редактировании общая сумма существующего графика должна сохраниться: "+requiredPaymentTotal+" ₽. Оплаченные этапы сохраняют свои PaymentPlan_ID.");
        }
        Button addPayment=button("Добавить платёж");addPayment.setOnClickListener(v->addPayment(paymentList,null));content.addView(addPayment);

        note=field(content,"Комментарий монтажникам",editing?saved.optString("Комментарий",""):"",false);
        Button save=button(editing?"Сохранить изменения ТЗ":"Сохранить ТЗ в Google Sheets");save.setOnClickListener(v->save(save));content.addView(save);
    }

    private JSONObject findTask(JSONObject snapshot,String objectId){
        if(snapshot==null)return null;JSONArray rows=snapshot.optJSONArray("techTasks");if(rows==null)return null;
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&objectId.equals(row.optString("Object_ID")))return row;}return null;
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private void label(LinearLayout parent,String text){TextView view=new TextView(activity);view.setText(text);view.setTextSize(13);view.setPadding(0,dp(8),0,dp(8));parent.addView(view);}
    private void title(LinearLayout parent,String text){TextView view=new TextView(activity);view.setText(text);view.setTextSize(18);view.setTypeface(null,Typeface.BOLD);view.setPadding(0,dp(18),0,dp(8));parent.addView(view);}
    private LinearLayout card(LinearLayout parent){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);view.setPadding(dp(16),dp(12),dp(16),dp(12));GradientDrawable background=new GradientDrawable();background.setColor(Color.rgb(246,248,251));background.setCornerRadius(dp(22));view.setBackground(background);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(10);parent.addView(view,params);return view;}
    private EditText field(LinearLayout parent,String title,String value,boolean number){label(parent,title);EditText edit=new EditText(activity);edit.setText(value==null?"":value);edit.setMinHeight(dp(48));if(number)edit.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);parent.addView(edit);return edit;}
    private EditText date(LinearLayout parent,String title,String value){String formatted=value==null?"":value;try{if(formatted.contains("."))formatted=LocalDate.parse(formatted,java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu")).toString();}catch(Exception ignored){}EditText field=field(parent,title,formatted,false);field.setFocusable(false);field.setOnClickListener(v->{LocalDate initial=LocalDate.now();try{initial=LocalDate.parse(field.getText().toString());}catch(Exception ignored){}new DatePickerDialog(activity,(picker,y,m,d)->field.setText(LocalDate.of(y,m+1,d).toString()),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();});return field;}
    private Button button(String text){Button button=new Button(activity);button.setText(text);button.setAllCaps(false);button.setMinHeight(dp(48));return button;}
    private long asLong(JSONObject row,String primary,String legacy){Object value=row.opt(primary);if(value==null||value==JSONObject.NULL||String.valueOf(value).isEmpty())value=row.opt(legacy);try{return Math.round(Double.parseDouble(String.valueOf(value)));}catch(Exception ignored){return 0;}}
    private double asDouble(JSONObject row,String key){try{return Double.parseDouble(String.valueOf(row.opt(key)));}catch(Exception ignored){return 0;}}

    private void addWorker(String id,String name,JSONObject existing){
        LinearLayout box=card(content);CheckBox selected=new CheckBox(activity);selected.setText(name+" · "+id);box.addView(selected);
        long current=existing==null?0:asLong(existing,"agreedAmount","Согласованная сумма");
        EditText amount=field(box,"Согласованная сумма, ₽",current>0?String.valueOf(current):"",true);
        String assignmentId=existing==null?"":existing.optString("Assignment_ID",existing.optString("ID"));
        int revision=existing==null?0:existing.optInt("revision",existing.optInt("Revision",1));
        if(existing!=null){selected.setChecked(true);selected.setEnabled(false);label(box,"Assignment_ID: "+assignmentId+" · revision "+revision+" — ID будет сохранён.");}
        workers.add(new Worker(id,name,selected,amount,assignmentId,revision));
    }

    private void addStage(LinearLayout list,JSONObject existing){
        LinearLayout box=card(list);
        String name=existing==null?"":existing.optString("Задача");
        String when=existing==null?object.planStart:existing.optString("Дата");
        String unit=existing==null?"":existing.optString("Ед. изм.");
        String qty=existing==null?"":String.valueOf(existing.opt("План объём")==null?"":existing.opt("План объём"));
        Stage stage=new Stage(field(box,"Название этапа / задача",name,false),date(box,"Дата выполнения",when),field(box,"Единица измерения",unit,false),field(box,"Плановый объём",qty,true),
                existing==null?"":existing.optString("DayPlan_ID",existing.optString("ID")),existing==null?0:existing.optInt("Revision",1));
        stages.add(stage);
        Button remove=button(existing==null?"Удалить этап из формы":"Существующий этап: удаление отдельно");
        if(existing==null)remove.setOnClickListener(v->{stages.remove(stage);list.removeView(box);});else remove.setEnabled(false);
        box.addView(remove);
    }

    private void addPayment(LinearLayout list,JSONObject existing){
        LinearLayout box=card(list);
        String name=existing==null?"":existing.optString("stageName");
        String when=existing==null?"":existing.optString("plannedDate");
        long amount=existing==null?0:Math.round(existing.optDouble("plannedAmount",0));
        Payment payment=new Payment(field(box,"Название платежа",name,false),date(box,"Дата платежа",when),field(box,"Сумма, ₽",amount>0?String.valueOf(amount):"",true),
                existing==null?"":existing.optString("id"),existing==null?0:existing.optInt("revision",1));
        payments.add(payment);
        Button remove=button(existing==null?"Удалить платёж из формы":"Существующий платёж: удаление отдельно");
        if(existing==null)remove.setOnClickListener(v->{payments.remove(payment);list.removeView(box);});else remove.setEnabled(false);
        box.addView(remove);
    }

    private String required(EditText field){String value=field.getText().toString().trim();if(value.isEmpty())throw new IllegalArgumentException("Заполните все обязательные поля");return value;}
    private long positiveMoney(EditText field){double parsed=Double.parseDouble(required(field).replace(',','.'));if(!Double.isFinite(parsed)||parsed<=0||Math.rint(parsed)!=parsed)throw new IllegalArgumentException("Суммы в ТЗ указываются целыми рублями");return Math.round(parsed);}
    private double positiveNumber(EditText field){double value=Double.parseDouble(required(field).replace(',','.'));if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException("Суммы и объёмы должны быть больше нуля");return value;}

    private void save(Button save){
        if(!api.hasToken()){Toast.makeText(activity,"Сначала подключите устройство к API",Toast.LENGTH_LONG).show();return;}
        try{
            LocalDate from=LocalDate.parse(required(start)),to=LocalDate.parse(required(end));if(to.isBefore(from))throw new IllegalArgumentException("Окончание раньше начала");
            JSONObject body=new JSONObject();body.put("objectId",object.id);body.put("engineerId",api.getUserId());body.put("engineer",api.getUserName());body.put("workType",object.workType);body.put("planStart",from.toString());body.put("planEnd",to.toString());body.put("materialsReady",materials.isChecked());body.put("status",materials.isChecked()?"Готово":"Черновик");body.put("comment",note.getText().toString());
            if(editing){body.put("techTaskId",techTaskId);body.put("expectedRevision",techTaskRevision);body.put("reason",required(reason));}

            JSONArray assignments=new JSONArray();for(Worker worker:workers)if(worker.selected.isChecked()){
                JSONObject row=new JSONObject();row.put("installerId",worker.id);row.put("name",worker.name);row.put("agreedAmount",positiveMoney(worker.amount));row.put("from",from.toString());row.put("to",to.toString());row.put("status","Назначен");
                if(!worker.assignmentId.isEmpty()){row.put("assignmentId",worker.assignmentId);row.put("expectedRevision",worker.revision);}assignments.put(row);
            }if(assignments.length()==0)throw new IllegalArgumentException("Выберите монтажника и согласованную сумму");body.put("assignments",assignments);

            JSONArray days=new JSONArray();for(Stage stage:stages){
                LocalDate date=LocalDate.parse(required(stage.date));if(date.isBefore(from)||date.isAfter(to))throw new IllegalArgumentException("Этап выходит за даты объекта");
                JSONObject row=new JSONObject();row.put("dayNo",java.time.temporal.ChronoUnit.DAYS.between(from,date)+1);row.put("date",date.toString());row.put("task",required(stage.name));row.put("unit",required(stage.unit));row.put("plannedQty",positiveNumber(stage.quantity));row.put("status","План");
                if(!stage.id.isEmpty()){row.put("dayPlanId",stage.id);row.put("expectedRevision",stage.revision);}days.put(row);
            }if(days.length()==0)throw new IllegalArgumentException("Добавьте этап плана");body.put("days",days);

            JSONArray paymentRows=new JSONArray();long total=0;for(Payment payment:payments){
                long amount=positiveMoney(payment.amount);total=Math.addExact(total,amount);JSONObject row=new JSONObject();row.put("stageNo",paymentRows.length()+1);row.put("stageName",required(payment.name));row.put("plannedDate",LocalDate.parse(required(payment.date)).toString());row.put("plannedAmount",amount);
                if(!payment.id.isEmpty()){row.put("paymentPlanId",payment.id);row.put("expectedRevision",payment.revision);}paymentRows.put(row);
            }
            if(total!=requiredPaymentTotal)throw new IllegalArgumentException("Сумма графика должна быть "+requiredPaymentTotal+" ₽");body.put("payments",paymentRows);

            save.setEnabled(false);save.setText("Сохраняем…");String action=editing?"saveTechTaskV2":"saveTechTask";
            api.mutate(action,body,new ApiClient.Callback(){public void onSuccess(JSONObject json){refresh.run();}public void onError(String error){save.setEnabled(true);save.setText(editing?"Сохранить изменения ТЗ":"Сохранить ТЗ в Google Sheets");new android.app.AlertDialog.Builder(activity).setMessage(error).setPositiveButton("Закрыть",null).show();}});
        }catch(Exception error){Toast.makeText(activity,error instanceof IllegalArgumentException?error.getMessage():"Проверьте даты и суммы",Toast.LENGTH_LONG).show();}
    }

    private static final class Stage{final EditText name,date,unit,quantity;final String id;final int revision;Stage(EditText name,EditText date,EditText unit,EditText quantity,String id,int revision){this.name=name;this.date=date;this.unit=unit;this.quantity=quantity;this.id=id;this.revision=revision;}}
    private static final class Payment{final EditText name,date,amount;final String id;final int revision;Payment(EditText name,EditText date,EditText amount,String id,int revision){this.name=name;this.date=date;this.amount=amount;this.id=id;this.revision=revision;}}
    private static final class Worker{final String id,name,assignmentId;final CheckBox selected;final EditText amount;final int revision;Worker(String id,String name,CheckBox selected,EditText amount,String assignmentId,int revision){this.id=id;this.name=name;this.selected=selected;this.amount=amount;this.assignmentId=assignmentId;this.revision=revision;}}
}

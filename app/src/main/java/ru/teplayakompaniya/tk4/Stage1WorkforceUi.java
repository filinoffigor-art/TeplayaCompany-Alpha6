package ru.teplayakompaniya.tk4;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.InputType;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.*;
import java.text.NumberFormat;

/** Additional forms use existing IDs. Unsupported server commands remain visibly unavailable. */
public final class Stage1WorkforceUi {
    private final Activity activity;
    private final JSONObject snapshot;
    private final ApiClient api;
    private final Runnable refresh;
    private final boolean finance,admin;
    public Stage1WorkforceUi(Activity activity,JSONObject snapshot,ApiClient api,boolean finance,boolean admin,Runnable refresh){this.activity=activity;this.snapshot=snapshot;this.api=api;this.finance=finance;this.admin=admin;this.refresh=refresh;}
    private String money(long amount){return NumberFormat.getNumberInstance(new Locale("ru","RU")).format(amount)+" ₽";}
    private LinearLayout form(){LinearLayout form=new LinearLayout(activity);form.setOrientation(LinearLayout.VERTICAL);int pad=(int)(24*activity.getResources().getDisplayMetrics().density);form.setPadding(pad,0,pad,pad);return form;}
    private void label(LinearLayout form,String text){TextView view=new TextView(activity);view.setText(text);view.setTextSize(14);view.setPadding(0,12,0,8);form.addView(view);}
    private EditText field(LinearLayout form,String title,String value,boolean numeric){label(form,title);EditText edit=new EditText(activity);edit.setText(value);edit.setSingleLine(true);edit.setMinHeight((int)(48*activity.getResources().getDisplayMetrics().density));if(numeric)edit.setInputType(InputType.TYPE_CLASS_NUMBER);form.addView(edit);return edit;}
    private EditText date(LinearLayout form,String title,boolean optional){EditText field=field(form,title,optional?"":LocalDate.now().toString(),false);field.setFocusable(false);field.setOnClickListener(v->{LocalDate now=LocalDate.now();new DatePickerDialog(activity,(p,y,m,d)->field.setText(LocalDate.of(y,m+1,d).toString()),now.getYear(),now.getMonthValue()-1,now.getDayOfMonth()).show();});if(optional){Button clear=new Button(activity);clear.setText("Без возврата");clear.setOnClickListener(v->field.setText(""));form.addView(clear);}return field;}
    private Spinner options(LinearLayout form,String title,List<String> values){label(form,title);Spinner spinner=new Spinner(activity);spinner.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,values));form.addView(spinner);return spinner;}
    private boolean capability(String name){JSONObject caps=snapshot.optJSONObject("capabilities");return caps!=null&&caps.optBoolean(name,false);}
    private interface Body{JSONObject build()throws Exception;}
    private void commandDialog(String title,LinearLayout form,String capability,Body body){
        boolean ready=capability(capability);if(!ready)label(form,"Не подключено: сервер пока не поддерживает эту операцию. Форма ничего не сохраняет локально.");
        ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
        final String key=UUID.randomUUID().toString();
        dialog.setOnShowListener(d->{Button save=dialog.getButton(AlertDialog.BUTTON_POSITIVE);save.setEnabled(ready);save.setOnClickListener(v->{
            try{JSONObject request=body.build();request.put("idempotencyKey",key);save.setEnabled(false);save.setText("Сохраняем…");api.mutate(request.getString("action"),request,new ApiClient.Callback(){
                public void onSuccess(JSONObject result){dialog.dismiss();refresh.run();}
                public void onError(String error){save.setEnabled(true);save.setText("Повторить");new AlertDialog.Builder(activity).setMessage(error).setPositiveButton("Закрыть",null).show();}
            });}catch(Exception error){Toast.makeText(activity,"Проверьте даты, суммы, причину и наличие постоянных ID",Toast.LENGTH_LONG).show();}
        });});dialog.show();
    }
    private long positive(EditText edit){long value=Long.parseLong(edit.getText().toString().trim());if(value<=0)throw new IllegalArgumentException();return value;}
    private String required(String value){if(value==null||value.trim().isEmpty())throw new IllegalArgumentException();return value.trim();}
    private List<JSONObject> rows(String key){List<JSONObject> result=new ArrayList<>();JSONArray array=snapshot.optJSONArray(key);if(array!=null)for(int n=0;n<array.length();n++){JSONObject row=array.optJSONObject(n);if(row!=null)result.add(row);}return result;}
    public void reimburse(String person,JSONObject balance){
        if(!finance||balance==null)return;
        LinearLayout form=form();label(form,"Возмещение собственных средств не является повторным расходом компании.");
        if(balance.opt("balance") instanceof Number)label(form,"К возмещению: "+money(Stage1Ledger.personalFundsOutstanding(balance.optLong("balance"))));
        EditText amount=field(form,"Сумма","",true);EditText reason=field(form,"Основание / причина","",false);
        commandDialog("Возместить: "+person,form,"personalReimbursementsV1",()->{
            long value=positive(amount);Stage1Ledger.balanceAfterReimbursement(balance.getLong("balance"),value);
            JSONObject command=Stage1Contracts.command("reimbursePersonalFunds",required(reason.getText().toString()));command.put("Accountable_ID",required(balance.optString("Accountable_ID")));command.put("amount",value);command.put("expectedRevision",balance.getInt("revision"));return command;
        });
    }
    public void moveInstaller(String installerId){
        List<JSONObject> assignments=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(JSONObject row:rows("assignments"))if(installerId.equals(row.optString("Installer_ID"))){assignments.add(row);labels.add(row.optString("Assignment_ID")+" · "+row.optString("Object_ID"));}
        if(assignments.isEmpty()){new AlertDialog.Builder(activity).setMessage("Нет назначения с Assignment_ID. Сначала синхронизируйте назначения.").setPositiveButton("Понятно",null).show();return;}
        List<JSONObject> objects=rows("objects");List<String> objectNames=new ArrayList<>();for(JSONObject object:objects)objectNames.add(object.optString("id")+" · "+object.optString("address"));
        LinearLayout form=form();Spinner source=options(form,"Исходное назначение",labels);Spinner target=options(form,"Новый объект",objectNames);
        EditText start=date(form,"Дата перехода",false),back=date(form,"Дата возврата (необязательно)",true);
        Spinner type=options(form,"Расчёт оплаты",Arrays.asList("Дневная ставка","Фиксированная сумма"));
        EditText rate=field(form,"Дневная ставка на новом объекте","",true);
        EditText earned=field(form,"Фиксированная: начислить за выполненное","",true);
        EditText deferred=field(form,"Фиксированная: оставить на возвращение","",true);
        CheckBox close=new CheckBox(activity);close.setText("Закрыть исходное обязательство полностью");form.addView(close);
        EditText reason=field(form,"Причина перемещения","",false);
        label(form,"Новый период получает новый Assignment_ID. Предыдущие дни и выплаты сохраняются. Сервер проверяет пересечения и повторные начисления.");
        commandDialog("Переместить на другой объект",form,"assignmentPeriodsV1",()->{
            JSONObject from=assignments.get(source.getSelectedItemPosition()),to=objects.get(target.getSelectedItemPosition());
            if(from.optString("Object_ID").equals(to.optString("id")))throw new IllegalArgumentException();
            LocalDate date=LocalDate.parse(start.getText().toString());String returnDate=back.getText().toString();if(!returnDate.isEmpty()&&!LocalDate.parse(returnDate).isAfter(date))throw new IllegalArgumentException();
            JSONObject command=Stage1Contracts.command("moveInstaller",required(reason.getText().toString()));command.put("Installer_ID",required(installerId));command.put("Assignment_ID",required(from.optString("Assignment_ID")));command.put("Object_ID",required(to.optString("id")));command.put("transitionDate",date.toString());command.put("returnDate",returnDate);command.put("expectedRevision",from.getInt("revision"));
            if(type.getSelectedItemPosition()==0){command.put("paymentType","DAILY");command.put("dailyRate",positive(rate));}
            else{long now=Long.parseLong(earned.getText().toString()),later=Long.parseLong(deferred.getText().toString());if(Stage1Ledger.deferredFixedAmount(from.getLong("unallocatedFixedAmount"),now,close.isChecked())!=later)throw new IllegalArgumentException();command.put("paymentType","FIXED");command.put("accrueNow",now);command.put("deferAmount",later);command.put("closeObligation",close.isChecked());}
            return command;
        });
    }
    public void hiredDay(String installerId,String selectedObjectId){
        List<JSONObject> objects=rows("objects");List<String> labels=new ArrayList<>();int selection=0;for(JSONObject object:objects){if(object.optString("id").equals(selectedObjectId))selection=labels.size();labels.add(object.optString("id")+" · "+object.optString("address"));}
        LinearLayout form=form();EditText name=field(form,"Имя / ФИО","",false),phone=field(form,"Телефон (необязательно)","",false);Spinner object=options(form,"Объект",labels);object.setSelection(selection);
        EditText date=date(form,"Дата работы",false),work=field(form,"Вид работ","",false),rate=field(form,"Дневная ставка","",true),days=field(form,"Количество фактически отработанных дней","1",true),comment=field(form,"Комментарий","",false);
        TextView preview=new TextView(activity);preview.setText("Начисление — дни × ставка. Выплата учитывается отдельно.");form.addView(preview);
        android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){try{preview.setText("Начислить: "+money(Stage1Ledger.dayAccrual(Math.toIntExact(positive(days)),positive(rate)))+"; выплачено после создания: 0 ₽");}catch(Exception e){preview.setText("Укажите ставку и дни");}}public void afterTextChanged(android.text.Editable e){}};rate.addTextChangedListener(watcher);days.addTextChangedListener(watcher);
        if(installerId!=null){name.setVisibility(android.view.View.GONE);phone.setVisibility(android.view.View.GONE);label(form,"Installer_ID: "+installerId+". Будет добавлен новый рабочий период к прежнему человеку.");}
        commandDialog(installerId==null?"Добавить наёмника":"Добавить рабочий день",form,"hiredWorkersV1",()->{
            int count=Math.toIntExact(positive(days));long daily=positive(rate);LocalDate day=LocalDate.parse(date.getText().toString());
            JSONObject command=Stage1Contracts.command(installerId==null?"createHiredWorkerDay":"addHiredWorkerDay",comment.getText().toString());
            if(installerId==null){command.put("name",required(name.getText().toString()));command.put("phone",phone.getText().toString());}else command.put("Installer_ID",required(installerId));
            command.put("Object_ID",required(objects.get(object.getSelectedItemPosition()).optString("id")));command.put("workDate",day.toString());command.put("actualDays",count);command.put("dailyRate",daily);command.put("previewAccrual",Stage1Ledger.dayAccrual(count,daily));command.put("workType",required(work.getText().toString()));return command;
        });
    }
    public void promote(String installerId){if(!admin)return;LinearLayout form=form();label(form,"Installer_ID и вся история назначений и выплат сохраняются. Создание аккаунта — отдельная операция.");EditText reason=field(form,"Причина","",false);commandDialog("Перевести в монтажники",form,"hiredWorkersV1",()->{JSONObject command=Stage1Contracts.command("promoteHiredWorker",required(reason.getText().toString()));command.put("Installer_ID",required(installerId));return command;});}
}

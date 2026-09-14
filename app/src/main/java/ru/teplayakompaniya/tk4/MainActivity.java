package ru.teplayakompaniya.tk4;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    // Palette from approved mockups
    private static final int GREEN = Color.rgb(7,132,79);
    private static final int GREEN_DARK = Color.rgb(8,107,73);
    private static final int ORANGE = Color.rgb(255,122,22);
    private static final int BLUE = Color.rgb(52,120,229);
    private static final int RED = Color.rgb(244,63,78);
    private static final int INK = Color.rgb(15,31,54);
    private static final int MUTED = Color.rgb(108,122,144);
    private static final int LINE = Color.rgb(230,236,243);
    private static final int BG = Color.rgb(248,250,252);
    private static final int WHITE = Color.WHITE;

    private FrameLayout root;
    private LinearLayout content;
    private final Deque<String> history = new ArrayDeque<>();
    private String screen = "main";

    // Demo state. Later replaced by Google Sheets/API without changing screen contracts.
    private final List<ObjectItem> objects = new ArrayList<>();
    private final List<InstallerItem> installers = new ArrayList<>();
    private final List<MoneyTx> txs = new ArrayList<>();
    private final List<SurveyItem> surveys = new ArrayList<>();
    private final List<EngineerItem> engineers = new ArrayList<>();
    private final List<ManagerItem> managers = new ArrayList<>();
    private final Map<String, TechTask> techTasks = new HashMap<>();

    private long igorBalance = 420_000;
    private long konstantinBalance = 386_000;
    private String currentPeriod = "Месяц";
    private String objectFilter = "Все";
    private String financeFilter = "Все";
    private String analyticsFilter = "Все объекты";
    private int selectedCalendarDay = 12;
    private String themeMode = "Светлая";
    private String leaderName = "Игорь Игоревич";
    private String demoRole = "Руководитель";
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("tk4_demo", MODE_PRIVATE);
        loadDemoState();
        configureSystemBars();

        root = new FrameLayout(this);
        root.setBackgroundColor(screenBg());
        setContentView(root);
        showMain(false);
    }

    private void configureSystemBars() {
        Window w = getWindow();
        int bg = "Тёмная".equals(themeMode) ? Color.rgb(18,24,33) : WHITE;
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            int flags = 0;
            if (!"Тёмная".equals(themeMode)) {
                flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            w.getDecorView().setSystemUiVisibility(flags);
        } else if (android.os.Build.VERSION.SDK_INT >= 23) {
            w.getDecorView().setSystemUiVisibility("Тёмная".equals(themeMode) ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        // No fullscreen and no edge-to-edge: content always stays inside Android system bars.
    }

    private void seedDemoData() {
        objects.clear(); installers.clear(); txs.clear(); surveys.clear(); engineers.clear(); managers.clear(); techTasks.clear();

        objects.add(new ObjectItem("OBJ-001","Мытищи, ул. Лесная, 12","Сидоров А.В.","Комплексное утепление","В работе",65,690000,450000,"Константин","Игорь Игоревич","Алексей Смирнов, Илья Орлов"));
        objects.add(new ObjectItem("OBJ-002","Королёв, ул. Полевая, 7","Петрова Е.С.","Утепление","Запланирован",0,600000,0,"Константин","Игорь Игоревич","Сергей Плотников"));
        objects.add(new ObjectItem("OBJ-003","Пушкино, СНТ Берёзка","Иванов М.С.","Тёплый пол","В работе",25,800000,200000,"Константин","Игорь Игоревич","Алексей Смирнов"));
        objects.add(new ObjectItem("OBJ-004","Ивантеевка, ул. Южная, 3","Кузнецов О.В.","Фасад + утепление","Подтверждён клиентом",0,500000,0,"Константин","Игорь Игоревич","Не назначены"));
        objects.add(new ObjectItem("OBJ-005","Химки, ул. Молодёжная, 21","Орлов П.Н.","Утепление мансарды","Завершён",100,430000,430000,"Константин","Мария","Андрей Крылов, Сергей Плотников"));

        installers.add(new InstallerItem("INS-001","Алексей Смирнов",22,8,4,92000,30000,3,"12.08.2026","На объекте"));
        installers.add(new InstallerItem("INS-002","Илья Орлов",20,10,3,94000,20000,3,"12.08.2026","На объекте"));
        installers.add(new InstallerItem("INS-003","Сергей Плотников",18,12,2,71000,15000,4,"01.09.2026","Выходной"));
        installers.add(new InstallerItem("INS-004","Андрей Крылов",21,9,4,88000,40000,5,"20.07.2026","Свободен"));

        txs.add(new MoneyTx("INCOME","Промежуточный платёж","Мытищи, ул. Лесная, 12",350000));
        txs.add(new MoneyTx("INCOME","Промежуточный платёж","Пушкино, СНТ Берёзка",220000));
        txs.add(new MoneyTx("INCOME","Финальный платёж","Химки, ул. Молодёжная, 21",675000));
        txs.add(new MoneyTx("EXPENSE","Материалы","Пушкино, СНТ Берёзка",125000));
        txs.add(new MoneyTx("EXPENSE","Зарплата / аванс","Монтажник: Сергей Плотников",80000));
        txs.add(new MoneyTx("EXPENSE","Маркетинг","Яндекс Директ",90000));
        txs.add(new MoneyTx("EXPENSE","Аренда","Офис и склад",200000));
        txs.add(new MoneyTx("EXPENSE","Топливо","Объекты месяца",60000));
        txs.add(new MoneyTx("EXPENSE","Материалы","Мытищи, ул. Лесная, 12",145000));
        txs.add(new MoneyTx("EXPENSE","Зарплата монтажников","Объекты месяца",120000));
        txs.add(new MoneyTx("TRANSFER","Константин → Игорь","Внутренний перевод",50000));

        surveys.add(new SurveyItem("Z-101","Смирнов Андрей","+7 916 220-44-18","02.09.2026",10,420000,false));
        surveys.add(new SurveyItem("Z-102","Козлова Ирина","+7 903 555-31-74","28.08.2026",16,350000,false));
        surveys.add(new SurveyItem("Z-103","Романов Сергей","+7 925 112-77-06","18.08.2026",25,610000,false));
        surveys.add(new SurveyItem("Z-104","Лебедев Олег","+7 977 680-90-11","10.09.2026",2,280000,false));
        surveys.add(new SurveyItem("Z-105","Васильева Анна","+7 985 101-42-90","05.09.2026",7,530000,true));

        engineers.add(new EngineerItem("ENG-1","Константин",6,3,2,1));
        engineers.add(new EngineerItem("ENG-2","Александр",3,2,0,1));
        managers.add(new ManagerItem("MGR-1","Игорь Игоревич",18,7,4,3));
        managers.add(new ManagerItem("MGR-2","Мария",14,5,3,2));

        TechTask t = new TechTask("OBJ-001");
        t.installers.put("Алексей Смирнов",45000L);
        t.installers.put("Илья Орлов",42000L);
        t.saved = true;
        techTasks.put("OBJ-001",t);

        igorBalance=420000; konstantinBalance=386000;
        currentPeriod="Месяц"; objectFilter="Все"; financeFilter="Все"; analyticsFilter="Все объекты"; selectedCalendarDay=12;
    }

    // ---------- navigation ----------

    private void navigate(String target) {
        if (!screen.equals(target)) history.push(screen);
        screen = target;
        render();
    }

    private void render() {
        configureSystemBars();
        switch (screen) {
            case "main": showMain(true); break;
            case "objects": showObjects(); break;
            case "create": showCreateObject(); break;
            case "installers": showInstallers(); break;
            case "finance": showFinance(); break;
            case "analytics": showAnalytics(); break;
            case "calendar": showCalendar(); break;
            case "settings": showSettings(); break;
            case "engineers": showEngineers(); break;
            case "managers": showManagers(); break;
            case "surveys": showSurveys(); break;
            case "newEmployee": showNewEmployee(); break;
            default:
                if (screen.startsWith("object:")) showObjectDetail(screen.substring(7));
                else if (screen.startsWith("installer:")) showInstallerDetail(screen.substring(10));
                else if (screen.startsWith("engineer:")) showEngineerDetail(screen.substring(9));
                else if (screen.startsWith("manager:")) showManagerDetail(screen.substring(8));
                else if (screen.startsWith("survey:")) showSurveyDetail(screen.substring(7));
                else if (screen.startsWith("kpi:")) showKpiDetail(screen.substring(4));
                else if (screen.startsWith("tech:")) showTechTask(screen.substring(5));
                else if (screen.startsWith("payments:")) showPayments(screen.substring(9));
                else if (screen.startsWith("photos:")) showPhotoReports(screen.substring(7));
                else if (screen.startsWith("accountable:")) showAccountable(screen.substring(12));
                else showMain(true);
        }
    }

    @Override
    public void onBackPressed() {
        if (!history.isEmpty()) {
            screen = history.pop();
            render();
        } else {
            super.onBackPressed();
        }
    }

    // ---------- frame / reusable UI ----------

    private void beginScreen(boolean showBottomNav) {
        root.removeAllViews();
        root.setBackgroundColor(screenBg());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(screenBg());

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(10), dp(16), dp(showBottomNav ? 92 : 24));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(scroll, sp);
        if (showBottomNav) addBottomNav();
    }

    private void addBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(5), dp(6), dp(5));
        nav.setBackgroundColor(cardBg());
        nav.setElevation(dp(14));

        String[][] items = {
                {"⌂","Главная","main"},
                {"☑","План","calendar"},
                {"+","Добавить","quickAdd"},
                {"♢","Уведомления","kpi:notifications"},
                {"○","Профиль","settings"}
        };
        for (String[] it : items) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setPadding(dp(4), dp(2), dp(4), dp(2));
            boolean active = it[2].equals(screen) || ("main".equals(it[2]) && "main".equals(screen));
            TextView icon = tv(it[0], it[2].equals("quickAdd") ? 28 : 22, active ? GREEN : MUTED, Typeface.BOLD);
            TextView label = tv(it[1], 10, active ? GREEN : MUTED, Typeface.NORMAL);
            box.addView(icon); box.addView(label);
            box.setOnClickListener(v -> { if ("quickAdd".equals(it[2])) showQuickAddDialog(); else navigate(it[2]); });
            nav.addView(box, new LinearLayout.LayoutParams(0, dp(64), 1));
        }
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74), Gravity.BOTTOM);
        root.addView(nav, np);
    }

    private void appBar(String title, String subtitle) {
        LinearLayout row = h();
        TextView back = pillText("‹", 26, INK, softBg());
        back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> onBackPressed());
        row.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = v();
        titles.addView(tv(title, 22, INK, Typeface.BOLD));
        titles.addView(tv(subtitle, 11, MUTED, Typeface.NORMAL));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.setMargins(dp(10),0,dp(8),0); row.addView(titles,tp);

        TextView menu = pillText("⋯",22,INK,softBg());
        menu.setGravity(Gravity.CENTER); menu.setOnClickListener(v -> showScreenMenu());
        row.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(44)));
        content.addView(row, lpMatch(dp(54), 0));
    }

    private void approvedHeader() {
        LinearLayout brandRow = h();
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.company_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setOnClickListener(v -> showAboutDialog());
        brandRow.addView(logo,new LinearLayout.LayoutParams(dp(46),dp(46)));

        LinearLayout b = v(); b.setPadding(dp(8),0,0,0);
        b.addView(tv("ТЁПЛАЯ КОМПАНИЯ",17,GREEN_DARK,Typeface.BOLD));
        b.addView(tv("Строим тепло вместе",11,MUTED,Typeface.NORMAL));
        brandRow.addView(b,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        TextView bell = pillText("♢",24,INK,Color.TRANSPARENT); bell.setGravity(Gravity.CENTER); bell.setOnClickListener(v -> navigate("kpi:notifications"));
        brandRow.addView(bell,new LinearLayout.LayoutParams(dp(44),dp(44))); content.addView(brandRow);

        LinearLayout hello = h();
        TextView avatar = pillText(initials(leaderName),16,WHITE,GREEN_DARK); avatar.setGravity(Gravity.CENTER); avatar.setOnClickListener(v->navigate("settings"));
        hello.addView(avatar,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout htxt = v(); htxt.setPadding(dp(10),0,0,0);
        htxt.addView(tv("Добрый день,",12,MUTED,Typeface.NORMAL));
        htxt.addView(tv(leaderName,20,INK,Typeface.BOLD));
        htxt.addView(tv(demoRole,11,MUTED,Typeface.NORMAL));
        hello.addView(htxt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        LinearLayout date = v(); date.setGravity(Gravity.CENTER_VERTICAL);
        date.addView(tv("Сегодня",11,MUTED,Typeface.NORMAL));
        date.addView(tv("12 сентября 2026",13,INK,Typeface.BOLD)); hello.addView(date);
        LinearLayout.LayoutParams hp=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0); hp.setMargins(0,dp(8),0,dp(10)); content.addView(hello,hp);
    }

    private void periodSelector() {
        LinearLayout period = h();
        period.setPadding(dp(4),dp(4),dp(4),dp(4));
        period.setBackground(round(softBg(),18));
        String[] labels={"Сегодня","Неделя","Месяц","Квартал","Год"};
        for(String x:labels){
            boolean sel=x.equals(currentPeriod);
            TextView t=tv(x,11,sel?WHITE:MUTED,Typeface.NORMAL); t.setGravity(Gravity.CENTER);
            if(sel) t.setBackground(round(GREEN,14));
            t.setOnClickListener(v->{ currentPeriod=x; saveDemoState(); render(); });
            period.addView(t,new LinearLayout.LayoutParams(0,dp(40),1));
        }
        content.addView(period); spacer(10);
    }

    private void sectionTitle(String title, String action, View.OnClickListener actionClick) {
        LinearLayout r=h();
        TextView t=tv(title,18,INK,Typeface.BOLD);
        r.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        if(action!=null){
            TextView a=tv(action,11,MUTED,Typeface.NORMAL);
            if(actionClick!=null) a.setOnClickListener(actionClick);
            r.addView(a);
        }
        LinearLayout.LayoutParams rp=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);
        rp.setMargins(0,dp(15),0,dp(7));
        content.addView(r,rp);
    }

    private LinearLayout kpiCard(String icon, String label, String value, String delta, int tint, String target) {
        LinearLayout c=v();
        c.setPadding(dp(12),dp(11),dp(12),dp(10));
        c.setBackground(round(light(tint),18));
        c.setElevation(dp(1));

        LinearLayout top=h();
        TextView ico=pillText(icon,20,WHITE,tint);
        ico.setGravity(Gravity.CENTER);
        top.addView(ico,new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout labels=v();
        labels.setPadding(dp(8),0,0,0);
        labels.addView(tv(label,12,INK,Typeface.BOLD));
        labels.addView(tv(value,20,INK,Typeface.BOLD));
        top.addView(labels,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        top.addView(tv("›",23,MUTED,Typeface.NORMAL));
        c.addView(top);
        if(delta!=null&&!delta.isEmpty()) c.addView(tv(delta,11,delta.startsWith("↓")?RED:GREEN,Typeface.BOLD));
        if(target!=null) c.setOnClickListener(vv->navigate(target));
        return c;
    }

    private LinearLayout miniCard(String icon, String label, String value, int tint, String target) {
        LinearLayout c=v();
        c.setPadding(dp(9),dp(8),dp(9),dp(8));
        c.setBackground(round(WHITE,15));
        c.setElevation(dp(1));
        LinearLayout r=h();
        TextView i=tv(icon,19,tint,Typeface.BOLD);
        r.addView(i,new LinearLayout.LayoutParams(dp(28),dp(28)));
        LinearLayout txt=v();
        txt.addView(tv(label,10,MUTED,Typeface.NORMAL));
        txt.addView(tv(value,15,INK,Typeface.BOLD));
        r.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        c.addView(r);
        if(target!=null) c.setOnClickListener(vv->navigate(target));
        return c;
    }

    private LinearLayout quickCard(String icon, String label, int tint, String target) {
        LinearLayout c=v();
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(5),dp(9),dp(5),dp(9));
        c.setBackground(round(WHITE,15));
        c.setElevation(dp(1));
        c.addView(tv(icon,24,tint,Typeface.BOLD));
        c.addView(tv(label,10,INK,Typeface.BOLD));
        if(target!=null)c.setOnClickListener(v->navigate(target));
        return c;
    }

    private LinearLayout attention(String icon,String title,String sub,int tint,String target){
        LinearLayout r=h();
        r.setPadding(dp(9),dp(9),dp(9),dp(9));
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(round(WHITE,14));
        TextView i=pillText(icon,17,WHITE,tint);
        i.setGravity(Gravity.CENTER);
        r.addView(i,new LinearLayout.LayoutParams(dp(34),dp(34)));
        LinearLayout text=v();
        text.setPadding(dp(9),0,dp(4),0);
        text.addView(tv(title,12,INK,Typeface.BOLD));
        text.addView(tv(sub,10,MUTED,Typeface.NORMAL));
        r.addView(text,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        r.addView(tv("›",22,MUTED,Typeface.NORMAL));
        if(target!=null)r.setOnClickListener(v->navigate(target));
        LinearLayout.LayoutParams p=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);
        p.setMargins(0,0,0,dp(6));
        r.setLayoutParams(p);
        return r;
    }

    // ---------- approved leader main ----------

    private void showMain(boolean fromNav) {
        screen="main"; if(fromNav) history.clear(); beginScreen(true); approvedHeader(); periodSelector();

        long turnover=actualTurnover();
        long expenses=actualExpenses();
        long profit=Math.max(0,turnover-expenses);
        int inWork=countStatus("В работе");
        int planned=countStatus("Запланирован")+countStatus("Подтверждён клиентом");

        LinearLayout row1=h(); row1.setWeightSum(2f);
        row1.addView(kpiCard("₽","Оборот",money(turnover),"Фактические поступления",GREEN,"kpi:turnover"),weight());
        row1.addView(kpiCard("▥","Прибыль",money(profit),"Оборот − фактические расходы",BLUE,"kpi:profit"),weightMarginLeft());
        content.addView(row1); spacer(8);

        LinearLayout row2=h(); row2.setWeightSum(2f);
        row2.addView(kpiCard("⌂","Объекты в работе",String.valueOf(inWork),"Монтаж идёт сейчас",GREEN,"kpi:objects_work"),weight());
        row2.addView(kpiCard("▦","Запланированные объекты",String.valueOf(planned),"Очередь будущих монтажей",ORANGE,"kpi:planned_objects"),weightMarginLeft());
        content.addView(row2); spacer(8);

        LinearLayout plannedReceipts=kpiCard("◷","Планируется поступление",money(plannedReceiptsTotal()),"Будущие этапы оплат из ТЗ",BLUE,"kpi:plan_income");
        content.addView(plannedReceipts);

        sectionTitle("Ключевые показатели",currentPeriod+"⌄",v->showPeriodDialog());
        addThreeMini(miniCard("●●","Лиды",String.valueOf((int)periodValue(42)),BLUE,"kpi:leads"),
                miniCard("▰","Замеры",String.valueOf((int)periodValue(20)),ORANGE,"surveys"),
                miniCard("▣","Договоры",String.valueOf(contractCountForPeriod()),GREEN,"kpi:contracts"));
        spacer(7);
        addThreeMini(miniCard("≋","Средний чек",money(averageCheck()),ORANGE,"kpi:avg"),
                miniCard("◷","Дебиторка",money(debtTotal()),RED,"kpi:debt"),
                miniCard("↓","Расходы",money(expenses),RED,"finance"));

        sectionTitle("Быстрый доступ",null,null);
        addFourQuick(quickCard("⌂","Объекты",GREEN,"objects"),quickCard("●●","Монтажники",ORANGE,"installers"),quickCard("♟","Инженеры",BLUE,"engineers"),quickCard("●●","Менеджеры",RED,"managers"));
        spacer(7);
        addFourQuick(quickCard("₽","Финансы",GREEN,"finance"),quickCard("▥","Аналитика",BLUE,"analytics"),quickCard("▦","Календарь",RED,"calendar"),quickCard("⚙","Справочники",MUTED,"settings"));

        sectionTitle("Сегодня требует внимания","Все уведомления ›",v->navigate("kpi:notifications"));
        content.addView(attention("◷","Просрочен платёж","Королёв, ул. Полевая, 7 · 200 000 ₽",RED,"payments:OBJ-002"));
        content.addView(attention("▰","Замеры сегодня не обновлены","Инженеру нужно внести данные за день",ORANGE,"surveys"));
        content.addView(attention("●","Лиды сегодня не обновлены","SMM должен внести количество лидов",ORANGE,"kpi:leads"));
        content.addView(attention("▤","Подтверждённый объект без полного ТЗ","Ивантеевка, ул. Южная, 3",RED,"tech:OBJ-004"));
    }

    // ---------- objects ----------

    private void showObjects() {
        beginScreen(true); appBar("Объекты","Все объекты компании"); sectionTitle("Объекты компании","+ Новый объект",v->navigate("create"));

        LinearLayout a=h(); a.addView(miniCard("⌂","В работе",String.valueOf(countStatus("В работе")),GREEN,"kpi:objects_work"),weight());
        a.addView(miniCard("▦","Запланированы",String.valueOf(countStatus("Запланирован")),BLUE,"kpi:objects_planned"),weightMarginLeft()); content.addView(a); spacer(7);
        LinearLayout b=h(); b.addView(miniCard("✓","Подтверждены",String.valueOf(countStatus("Подтверждён клиентом")),ORANGE,"kpi:objects_confirmed"),weight());
        b.addView(miniCard("▰","Замеры за месяц",String.valueOf(surveys.size()),BLUE,"surveys"),weightMarginLeft()); content.addView(b); spacer(7);
        content.addView(miniCard("↪","В монтаж перешли",String.valueOf(countConvertedSurveys()),BLUE,"surveys"));

        spacer(10); LinearLayout filters=h();
        String[] fs={"Все","В работе","Запланирован","Подтверждён клиентом","Завершён"};
        for(String f:fs){
            boolean sel=f.equals(objectFilter); String label=f.equals("Запланирован")?"Запланированы":f.equals("Подтверждён клиентом")?"Подтверждены":f.equals("Завершён")?"Завершены":f;
            TextView chip=pillText(label,10,sel?WHITE:INK,sel?GREEN:softBg()); chip.setPadding(dp(11),dp(7),dp(11),dp(7));
            chip.setOnClickListener(v->{objectFilter=f;showObjects();});
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(34)); cp.setMargins(0,0,dp(5),0); filters.addView(chip,cp);
        }
        HorizontalScrollView hsv=new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); hsv.addView(filters); content.addView(hsv);

        LinearLayout search=h(); EditText e=edit("Поиск по адресу, клиенту…"); search.addView(e,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView plus=pillText("+",28,WHITE,GREEN); plus.setGravity(Gravity.CENTER); plus.setOnClickListener(v->navigate("create"));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(46),dp(46));pp.setMargins(dp(7),0,0,0);search.addView(plus,pp);
        LinearLayout.LayoutParams sr=lpMatch(dp(50),0);sr.setMargins(0,dp(10),0,dp(4));content.addView(search,sr);

        LinearLayout list=v(); content.addView(list);
        Runnable rebuild=()->{
            list.removeAllViews(); String q=e.getText().toString().trim().toLowerCase(Locale.ROOT); int shown=0;
            for(ObjectItem o:objects){
                boolean statusOk="Все".equals(objectFilter)||o.status.equals(objectFilter);
                boolean searchOk=q.isEmpty()||o.address.toLowerCase(Locale.ROOT).contains(q)||o.client.toLowerCase(Locale.ROOT).contains(q)||o.workType.toLowerCase(Locale.ROOT).contains(q);
                if(statusOk&&searchOk){list.addView(objectCard(o));shown++;}
            }
            if(shown==0) list.addView(emptyState("Ничего не найдено","Измените фильтр или строку поиска."));
        };
        e.addTextChangedListener(new SimpleWatcher(rebuild)); rebuild.run();
    }

    private View objectCard(ObjectItem o) {
        LinearLayout card=h();
        card.setPadding(dp(10),dp(10),dp(10),dp(10));
        card.setBackground(round(WHITE,16));
        card.setElevation(dp(1));

        TextView photo=pillText("⌂",26,GREEN,Color.rgb(235,247,241));
        photo.setGravity(Gravity.CENTER);
        card.addView(photo,new LinearLayout.LayoutParams(dp(64),dp(64)));

        LinearLayout mid=v(); mid.setPadding(dp(10),0,0,0);
        mid.addView(tv(o.address,13,INK,Typeface.BOLD));
        mid.addView(tv("Клиент: "+o.client,10,MUTED,Typeface.NORMAL));
        mid.addView(tv(o.workType,10,MUTED,Typeface.NORMAL));
        mid.addView(tv("Исполнители: "+o.installers,9,MUTED,Typeface.NORMAL));
        card.addView(mid,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        LinearLayout right=v();right.setGravity(Gravity.RIGHT);
        right.addView(statusBadge(o.status));
        if(o.progress>0) right.addView(tv(o.progress+"%",11,GREEN,Typeface.BOLD));
        right.addView(tv("›",22,MUTED,Typeface.NORMAL));
        card.addView(right);

        card.setOnClickListener(v->navigate("object:"+o.id));
        LinearLayout.LayoutParams p=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);p.setMargins(0,dp(5),0,dp(4));card.setLayoutParams(p);
        return card;
    }

    private void showObjectDetail(String id) {
        ObjectItem o=findObject(id); if(o==null){navigate("objects");return;}
        beginScreen(true); appBar("Карточка объекта",o.address); content.addView(infoHero(o.address,o.status,o.progress));

        LinearLayout actions=h();
        Button call=smallButton("☎ Клиент",GREEN); call.setOnClickListener(v->dial("+79151234567"));
        Button map=smallButton("⌖ Адрес",BLUE); map.setOnClickListener(v->openMap(o.address));
        Button status=smallButton("Статус",ORANGE); status.setOnClickListener(v->changeObjectStatus(o));
        actions.addView(call,weight());actions.addView(map,weightMarginLeft());actions.addView(status,weightMarginLeft()); content.addView(actions,lpMatch(dp(46),8));

        sectionTitle("Клиент и объект",null,null);
        content.addView(clickableInfoRow("Клиент",o.client,v->dial("+79151234567")));
        content.addView(clickableInfoRow("Телефон","+7 915 123-45-67",v->dial("+79151234567")));
        content.addView(clickableInfoRow("Адрес",o.address,v->openMap(o.address)));
        content.addView(infoRow("Вид работ",o.workType)); content.addView(clickableInfoRow("Инженер",o.engineer,v->navigate("engineers")));
        content.addView(clickableInfoRow("Менеджер",o.manager,v->navigate("managers"))); content.addView(clickableInfoRow("Монтажники",o.installers,v->navigate("installers")));

        sectionTitle("Финансы объекта",null,null); LinearLayout f=h();
        f.addView(miniCard("₽","Договор",money(o.contract),GREEN,"kpi:contract_object"),weight());
        f.addView(miniCard("↓","Получено",money(o.paid),BLUE,"payments:"+o.id),weightMarginLeft()); content.addView(f); spacer(7);
        content.addView(miniCard("◷","Осталось получить",money(Math.max(0,o.contract-o.paid)),ORANGE,"payments:"+o.id));

        sectionTitle("Управление объектом",null,null);
        content.addView(attention("▤",techTasks.containsKey(o.id)&&techTasks.get(o.id).saved?"ТЗ создано":"Создать техническое задание","Монтажники · зарплата · план по дням · график оплат",ORANGE,"tech:"+o.id));
        content.addView(attention("₽","График платежей","Плановые, полученные и просроченные этапы",GREEN,"payments:"+o.id));
        content.addView(attention("▦","Фотоотчёты","ДО · процесс · ПОСЛЕ · комментарии по дням",BLUE,"photos:"+o.id));
        content.addView(attention("↓","Добавить расход по объекту","Материалы, топливо, инструмент, прочее",RED,"kpi:expense_object"));
    }

    private void showCreateObject() {
        beginScreen(true); appBar("Создать объект","Новый объект в системе");
        LinearLayout sync=v();sync.setPadding(dp(12),dp(10),dp(12),dp(10));sync.setBackground(round(light(GREEN),16));
        sync.addView(tv("● ДЕМО-БАЗА АКТИВНА",13,GREEN_DARK,Typeface.BOLD));
        sync.addView(tv("Сейчас запись хранится локально на телефоне. Google Sheets подключим следующим этапом.",10,MUTED,Typeface.NORMAL));content.addView(sync);

        final EditText client=field("Клиент *","Иванов Сергей Петрович"); final EditText phone=field("Телефон","+7 915 123-45-67");
        final EditText address=field("Адрес объекта *","Московская обл., г. Химки, ул. Лесная, д. 12");
        final EditText type=choiceField("Вид работ *","Комплексное утепление",new String[]{"Комплексное утепление","Утепление пола","Утепление стен","Мансарда","Фасад","Фасад + утепление"});
        final EditText sum=field("Сумма договора","350000"); final EditText survey=field("Дата замера","10.09.2026"); final EditText plan=field("Плановая дата монтажа","25.09.2026");
        final EditText status=choiceField("Статус объекта","Подтверждён клиентом",new String[]{"Подтверждён клиентом","Запланирован","В работе","Приостановлен"});
        final EditText engineer=choiceField("Инженер","Константин",engineerNames()); final EditText manager=choiceField("Менеджер","Игорь Игоревич",managerNames());

        sectionTitle("После сохранения",null,null);content.addView(attention("▤","Техническое задание","Можно сразу назначить монтажников, суммы и план по дням.",ORANGE,null));

        Button save=primary("Сохранить объект");
        save.setOnClickListener(v->{
            if(client.getText().toString().trim().isEmpty()||address.getText().toString().trim().isEmpty()){toast("Заполните клиента и адрес");return;}
            long contract=parseLong(sum.getText().toString()); String id="OBJ-"+String.format(Locale.US,"%03d",objects.size()+1);
            ObjectItem o=new ObjectItem(id,address.getText().toString(),client.getText().toString(),type.getText().toString(),status.getText().toString(),0,contract,0,engineer.getText().toString(),manager.getText().toString(),"Не назначены");
            objects.add(0,o); saveDemoState();
            new AlertDialog.Builder(this).setTitle("Объект создан").setMessage("Открыть техническое задание сейчас?")
                    .setPositiveButton("Создать ТЗ",(d,w)->navigate("tech:"+id)).setNegativeButton("К объектам",(d,w)->navigate("objects")).show();
        }); content.addView(save);
    }

    // ---------- tech task ----------

    private void showTechTask(String objectId) {
        beginScreen(true); appBar("Техническое задание","Инженер · "+("NEW".equals(objectId)?"новый объект":objectId));
        TechTask existing=techTasks.get(objectId); if(existing==null) existing=new TechTask(objectId); final TechTask task=existing;
        final Map<String,CheckBox> checks=new LinkedHashMap<>(); final Map<String,EditText> wages=new LinkedHashMap<>();

        sectionTitle("Монтажники на объекте","Конкретные сотрудники",null);
        for(InstallerItem i:installers){
            LinearLayout r=h();r.setPadding(dp(9),dp(8),dp(9),dp(8));r.setBackground(round(cardBg(),14));
            CheckBox cb=new CheckBox(this);cb.setChecked(task.installers.containsKey(i.name));checks.put(i.name,cb);r.addView(cb,new LinearLayout.LayoutParams(dp(44),dp(44)));
            LinearLayout name=v();name.addView(tv(i.name,12,INK,Typeface.BOLD));name.addView(tv(i.status,9,MUTED,Typeface.NORMAL));r.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            EditText amount=edit("Сумма");amount.setInputType(InputType.TYPE_CLASS_NUMBER);Long saved=task.installers.get(i.name);amount.setText(saved==null?"40000":String.valueOf(saved));wages.put(i.name,amount);r.addView(amount,new LinearLayout.LayoutParams(dp(100),dp(44)));
            LinearLayout.LayoutParams rp=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);rp.setMargins(0,0,0,dp(6));content.addView(r,rp);
        }

        sectionTitle("План объекта по дням","Редактируется инженером",null);
        final EditText d1a=taskField("День 1 — задача 1",task.day1a); final EditText d1b=taskField("День 1 — задача 2",task.day1b);
        final EditText d2a=taskField("День 2 — задача 1",task.day2a); final EditText d2b=taskField("День 2 — задача 2",task.day2b);
        final EditText note=taskField("Комментарий монтажникам",task.note);

        sectionTitle("График оплат клиента","Формирует дебиторку и план поступлений",null);
        final EditText p1=taskField("25.09.2026 — платёж",String.valueOf(task.pay1));
        final EditText p2=taskField("27.09.2026 — платёж",String.valueOf(task.pay2));
        final EditText p3=taskField("30.09.2026 — платёж",String.valueOf(task.pay3));

        Button save=primary("Сохранить ТЗ");save.setOnClickListener(v->{
            task.installers.clear();
            for(InstallerItem i:installers){if(checks.get(i.name).isChecked())task.installers.put(i.name,Math.max(0,parseLong(wages.get(i.name).getText().toString())));}
            task.day1a=d1a.getText().toString();task.day1b=d1b.getText().toString();task.day2a=d2a.getText().toString();task.day2b=d2b.getText().toString();task.note=note.getText().toString();
            task.pay1=parseLong(p1.getText().toString());task.pay2=parseLong(p2.getText().toString());task.pay3=parseLong(p3.getText().toString());task.saved=true;techTasks.put(objectId,task);
            ObjectItem o=findObject(objectId);if(o!=null){o.installers=task.installers.isEmpty()?"Не назначены":joinNames(task.installers.keySet()); if(o.status.equals("Подтверждён клиентом"))o.status="Запланирован";}
            saveDemoState();toast("ТЗ сохранено. Монтажники и суммы закреплены.");
        });content.addView(save);spacer(7);
        Button share=primaryOutline("Поделиться ТЗ");share.setOnClickListener(v->{save.performClick();shareTechTask(objectId);});content.addView(share);
    }

    private View dayCard(String title,String t1,String q1,String t2,String q2){
        LinearLayout c=v();c.setPadding(dp(11),dp(10),dp(11),dp(10));c.setBackground(round(Color.rgb(249,251,253),15));
        c.addView(tv(title,13,INK,Typeface.BOLD));
        c.addView(infoRow(t1,q1));
        c.addView(infoRow(t2,q2));
        LinearLayout.LayoutParams p=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);p.setMargins(0,0,0,dp(7));c.setLayoutParams(p);
        return c;
    }

    private void shareTechTask(String objectId){
        TechTask t=techTasks.get(objectId); ObjectItem o=findObject(objectId);
        StringBuilder sb=new StringBuilder("ТЁПЛАЯ КОМПАНИЯ — ТЕХНИЧЕСКОЕ ЗАДАНИЕ\n");
        sb.append("Объект: ").append(o==null?objectId:o.address).append("\n");
        if(t!=null){
            sb.append("Монтажники:\n"); for(Map.Entry<String,Long> e:t.installers.entrySet())sb.append("• ").append(e.getKey()).append(" — ").append(money(e.getValue())).append("\n");
            sb.append("\nДень 1: ").append(t.day1a).append("; ").append(t.day1b).append("\n");
            sb.append("День 2: ").append(t.day2a).append("; ").append(t.day2b).append("\n");
            sb.append("Комментарий: ").append(t.note).append("\n");
            sb.append("\nГрафик оплат: ").append(money(t.pay1)).append(" / ").append(money(t.pay2)).append(" / ").append(money(t.pay3));
        }
        sb.append("\n\nФинансовая аналитика компании монтажникам не передаётся.");
        Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_SUBJECT,"ТЗ — "+objectId);send.putExtra(Intent.EXTRA_TEXT,sb.toString());
        startActivity(Intent.createChooser(send,"Поделиться ТЗ"));
    }

    // ---------- installers ----------

    private void showInstallers() {
        beginScreen(true); appBar("Монтажники","Аналитика, выплаты, инструмент"); periodSelector();
        long accrued=0,paid=0;for(InstallerItem i:installers){accrued+=i.accrued;paid+=i.paid;}
        LinearLayout r1=h();r1.addView(kpiCard("●●","Монтажников",String.valueOf(installers.size()),"Справочник сотрудников",ORANGE,"kpi:installers_count"),weight());
        r1.addView(kpiCard("⌂","На объектах сегодня",String.valueOf(countInstallerStatus("На объекте")),"Нажмите для календаря",GREEN,"calendar"),weightMarginLeft());content.addView(r1);spacer(8);
        LinearLayout r2=h();r2.addView(kpiCard("✓","Закрыто объектов",String.valueOf(totalClosedObjects()),periodDelta("+27%"),BLUE,"kpi:closed_by_installers"),weight());
        r2.addView(kpiCard("₽","К выдаче",money(accrued-paid),"Начислено − выплаты",ORANGE,"kpi:payroll"),weightMarginLeft());content.addView(r2);
        sectionTitle("Начисления и выплаты","+ Монтажник",v->showNewInstallerDialog());
        addThreeMini(miniCard("≋","Начислено",money(accrued),GREEN,"kpi:accrued"),miniCard("▭","Выдано / аванс",money(paid),ORANGE,"kpi:paid_installers"),miniCard("₽","Осталось",money(accrued-paid),RED,"kpi:payroll"));
        sectionTitle("Сотрудники","Нажмите на сотрудника ›",null); for(InstallerItem i:installers)content.addView(installerCard(i));
    }

    private View installerCard(InstallerItem i){
        LinearLayout c=v();c.setPadding(dp(10),dp(9),dp(10),dp(9));c.setBackground(round(WHITE,15));c.setElevation(dp(1));
        LinearLayout top=h();
        TextView ava=pillText(initials(i.name),14,WHITE,GREEN_DARK);ava.setGravity(Gravity.CENTER);top.addView(ava,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout name=v();name.setPadding(dp(9),0,0,0);name.addView(tv(i.name,12,INK,Typeface.BOLD));name.addView(tv("Монтажник · "+i.status,9,i.status.equals("Выходной")?ORANGE:GREEN,Typeface.NORMAL));
        top.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        top.addView(tv("›",22,MUTED,Typeface.NORMAL));
        c.addView(top);
        LinearLayout m=h();
        m.addView(metricTiny(i.workDays+"","раб. дней"),weight());
        m.addView(metricTiny(i.daysOff+"","выходных"),weight());
        m.addView(metricTiny(i.closedObjects+"","объекта"),weight());
        c.addView(m);
        LinearLayout money=h();
        money.addView(metricTiny(money(i.accrued),"начислено"),weight());
        money.addView(metricTiny(money(i.paid),"аванс/выплаты"),weight());
        money.addView(metricTiny(money(i.accrued-i.paid),"к выдаче"),weight());
        c.addView(money);
        c.addView(tv("Инструмент: "+i.tools+" ед. · Форма выдана: "+i.uniformDate,9,MUTED,Typeface.NORMAL));
        c.setOnClickListener(v->navigate("installer:"+i.id));
        LinearLayout.LayoutParams p=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);p.setMargins(0,0,0,dp(7));c.setLayoutParams(p);
        return c;
    }

    private void showInstallerDetail(String id){
        InstallerItem i=findInstaller(id);if(i==null){navigate("installers");return;}
        beginScreen(true);appBar(i.name,"Монтажник");content.addView(infoHero(i.name,i.status,0));
        LinearLayout acts=h(); Button adv=smallButton("+ Аванс",ORANGE);adv.setOnClickListener(v->installerPaymentDialog(i));
        Button tool=smallButton("+ Инструмент",BLUE);tool.setOnClickListener(v->{i.tools++;saveDemoState();toast("Инструмент закреплён");showInstallerDetail(id);});
        Button form=smallButton("Выдать форму",GREEN);form.setOnClickListener(v->{i.uniformDate="12.09.2026";saveDemoState();toast("Выдача формы зафиксирована");showInstallerDetail(id);});
        acts.addView(adv,weight());acts.addView(tool,weightMarginLeft());acts.addView(form,weightMarginLeft());content.addView(acts,lpMatch(dp(48),8));
        sectionTitle("Месяц",null,null);content.addView(infoRow("Рабочие дни",String.valueOf(i.workDays)));content.addView(infoRow("Выходные",String.valueOf(i.daysOff)));content.addView(infoRow("Закрыто объектов",String.valueOf(i.closedObjects)));
        content.addView(infoRow("Начислено",money(i.accrued)));content.addView(infoRow("Выдано / аванс",money(i.paid)));content.addView(infoRow("Осталось выдать",money(Math.max(0,i.accrued-i.paid))));
        sectionTitle("Имущество",null,null);content.addView(infoRow("Инструмент",i.tools+" единиц · закреплено"));content.addView(infoRow("Форма / СИЗ","Выдана "+i.uniformDate));
        sectionTitle("Объекты монтажника",null,null); for(ObjectItem o:objects)if(o.installers.contains(i.name))content.addView(clickableInfoRow(o.address,o.status,v->navigate("object:"+o.id)));
    }

    // ---------- finance ----------

    private void showFinance() {
        beginScreen(true);appBar("Финансы","Приходы, расходы и живые остатки");periodSelector();
        long turnover=actualTurnover(),expenses=actualExpenses();long totalBalance=igorBalance+konstantinBalance;
        LinearLayout a=h();a.addView(kpiCard("₽","Оборот",money(turnover),"Только фактические приходы",GREEN,"kpi:turnover"),weight());a.addView(kpiCard("↓","Расходы",money(expenses),"Внутренние переводы не входят",RED,"kpi:expenses"),weightMarginLeft());content.addView(a);spacer(8);
        LinearLayout b=h();b.addView(kpiCard("◉","Общий остаток",money(totalBalance),"Игорь + Константин",BLUE,"kpi:balance"),weight());b.addView(kpiCard("▣","Подотчёт",money(totalBalance),"По ответственным",ORANGE,"kpi:accountable"),weightMarginLeft());content.addView(b);
        sectionTitle("Остатки у подотчётных лиц","История ›",v->navigate("accountable:all"));content.addView(clickableInfoRow("Игорь",money(igorBalance),v->navigate("accountable:Игорь")));content.addView(clickableInfoRow("Константин",money(konstantinBalance),v->navigate("accountable:Константин")));
        LinearLayout acts=h();Button in=smallButton("+ Приход",GREEN);in.setOnClickListener(v->moneyDialog("INCOME"));Button out=smallButton("− Расход",ORANGE);out.setOnClickListener(v->moneyDialog("EXPENSE"));Button move=smallButton("↔ Передать",BLUE);move.setOnClickListener(v->transferDialog());acts.addView(in,weight());acts.addView(out,weightMarginLeft());acts.addView(move,weightMarginLeft());content.addView(acts,lpMatch(dp(48),10));
        sectionTitle("Операции",currentPeriod+"⌄",v->showPeriodDialog());
        LinearLayout filters=h();for(String f:new String[]{"Все","Приходы","Расходы","Переводы","Объекты","Монтажники","Маркетинг"}){boolean sel=f.equals(financeFilter);TextView chip=pillText(f,9,sel?WHITE:INK,sel?GREEN:softBg());chip.setPadding(dp(10),dp(7),dp(10),dp(7));chip.setOnClickListener(v->{financeFilter=f;showFinance();});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(34));cp.setMargins(0,0,dp(5),0);filters.addView(chip,cp);}HorizontalScrollView hv=new HorizontalScrollView(this);hv.setHorizontalScrollBarEnabled(false);hv.addView(filters);content.addView(hv);spacer(6);
        int shown=0;for(MoneyTx t:txs){if(financeMatches(t)){int tint=t.type.equals("INCOME")?GREEN:t.type.equals("EXPENSE")?RED:BLUE;String sign=t.type.equals("INCOME")?"+":t.type.equals("EXPENSE")?"−":"↔";content.addView(attention(sign,t.title,t.sub+" · "+money(t.amount),tint,null));shown++;}}if(shown==0)content.addView(emptyState("Операций нет","Для выбранного фильтра пока нет демо-записей."));
        sectionTitle("Требует внимания",null,null);content.addView(attention("!","Приход без получателя","Нужно указать подотчётное лицо",RED,"kpi:notifications"));content.addView(attention("!","Расход без объекта","Проверьте назначение расхода",ORANGE,"kpi:notifications"));
    }

    private void moneyDialog(String type){
        LinearLayout form=v();form.setPadding(dp(18),0,dp(18),0);
        EditText amt=edit("Сумма");amt.setInputType(InputType.TYPE_CLASS_NUMBER);form.addView(labelWrap("Сумма",amt));
        Spinner category=new Spinner(this);String[] cats=type.equals("INCOME")?new String[]{"Оплата клиента","Возврат","Прочий приход"}:new String[]{"Материалы","Зарплата/аванс","Топливо","Реклама","Аренда","Инструмент","Автомобили","Личные нужды","Прочее"};category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,cats));form.addView(labelWrap("Категория",category));
        Spinner owner=new Spinner(this);owner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Игорь","Константин"}));form.addView(labelWrap("Подотчётное лицо",owner));
        Spinner target=new Spinner(this);List<String> targets=new ArrayList<>();targets.add("Без привязки");for(ObjectItem o:objects)targets.add(o.address);for(InstallerItem i:installers)targets.add("Монтажник: "+i.name);target.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,targets));form.addView(labelWrap("Объект / монтажник",target));
        String title=type.equals("INCOME")?"Добавить приход":"Добавить расход";
        new AlertDialog.Builder(this).setTitle(title).setView(form).setPositiveButton("Сохранить",(d,w)->{
            long v=parseLong(amt.getText().toString());if(v<=0){toast("Сумма не указана");return;}String who=owner.getSelectedItem().toString();String sub=target.getSelectedItem().toString();String cat=category.getSelectedItem().toString();
            txs.add(0,new MoneyTx(type,cat,sub,v)); if("Игорь".equals(who))igorBalance+=type.equals("INCOME")?v:-v;else konstantinBalance+=type.equals("INCOME")?v:-v;
            if(type.equals("EXPENSE")&&sub.startsWith("Монтажник: ")){InstallerItem i=findInstallerByName(sub.substring(11));if(i!=null)i.paid+=v;}
            saveDemoState();showFinance();
        }).setNegativeButton("Отмена",null).show();
    }

    private void transferDialog(){
        LinearLayout form=v();form.setPadding(dp(18),0,dp(18),0);Spinner dir=new Spinner(this);dir.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Константин → Игорь","Игорь → Константин"}));form.addView(labelWrap("Направление",dir));EditText amt=edit("Сумма");amt.setInputType(InputType.TYPE_CLASS_NUMBER);form.addView(labelWrap("Сумма",amt));
        new AlertDialog.Builder(this).setTitle("Передать деньги").setMessage("Внутренний перевод не является расходом и не меняет общий баланс компании.").setView(form).setPositiveButton("Передать",(d,w)->{
            long v=parseLong(amt.getText().toString());String direction=dir.getSelectedItem().toString();if(v<=0)return;
            if(direction.startsWith("Константин")){if(v>konstantinBalance){toast("Недостаточно у Константина");return;}konstantinBalance-=v;igorBalance+=v;}else{if(v>igorBalance){toast("Недостаточно у Игоря");return;}igorBalance-=v;konstantinBalance+=v;}
            txs.add(0,new MoneyTx("TRANSFER",direction,"Внутренний перевод",v));saveDemoState();showFinance();
        }).setNegativeButton("Отмена",null).show();
    }

    // ---------- analytics ----------

    private void showAnalytics() {
        beginScreen(true);appBar("Аналитика","Объекты, поступления, дебиторка");periodSelector();
        LinearLayout a=h();a.addView(kpiCard("₽","Оборот",money(actualTurnover()),"Фактические приходы",GREEN,"kpi:turnover"),weight());a.addView(kpiCard("◷","Дебиторка",money(debtTotal()),"Только просрочено",RED,"kpi:debt"),weightMarginLeft());content.addView(a);spacer(8);
        LinearLayout b=h();b.addView(kpiCard("▥","План поступлений",money(plannedReceiptsTotal()),"Будущие этапы ТЗ",BLUE,"kpi:plan_income"),weight());b.addView(kpiCard("◔","Осталось получить",money(remainingReceiptsTotal()),"По договорам",ORANGE,"kpi:remaining"),weightMarginLeft());content.addView(b);
        sectionTitle("Фильтры",analyticsFilter+"⌄",null);LinearLayout filters=h();for(String f:new String[]{"Все объекты","В работе","Запланирован","Завершён","По инженеру","По менеджеру","По монтажнику","По финансам"}){boolean sel=f.equals(analyticsFilter);String label=f.equals("Запланирован")?"Запланированы":f.equals("Завершён")?"Завершены":f;TextView chip=pillText(label,9,sel?WHITE:INK,sel?GREEN:softBg());chip.setPadding(dp(10),dp(7),dp(10),dp(7));chip.setOnClickListener(v->{analyticsFilter=f;showAnalytics();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(34));p.setMargins(0,0,dp(5),0);filters.addView(chip,p);}HorizontalScrollView hv=new HorizontalScrollView(this);hv.setHorizontalScrollBarEnabled(false);hv.addView(filters);content.addView(hv);
        sectionTitle("Аналитика по объектам",objects.size()+" объектов ›",null);int shown=0;for(ObjectItem o:objects){if(analyticsObjectMatches(o)){long plan=Math.max(0,o.contract-o.paid);long debt=o.status.equals("Завершён")&&plan>0?plan:0;long future=debt>0?0:plan;View card=analyticsObject(o.address,o.status,o.contract,o.paid,future,debt,o.progress,debt>0?"Просрочено":"Следующий этап по ТЗ");card.setOnClickListener(v->navigate("object:"+o.id));content.addView(card);shown++;}}if(shown==0)content.addView(emptyState("Нет объектов","Измените фильтр."));
        sectionTitle("Требует внимания",null,null);content.addView(attention("!","Просрочен платёж — 200 000 ₽","Королёв, ул. Полевая, 7",RED,"kpi:debt"));content.addView(attention("◷","Через 2 дня ожидается 180 000 ₽","Мытищи, ул. Центральная, 8",ORANGE,"kpi:plan_income"));
    }

    private View analyticsObject(String addr,String status,long contract,long paid,long plan,long debt,int progress,String next){
        LinearLayout c=v();c.setPadding(dp(10),dp(9),dp(10),dp(9));c.setBackground(round(WHITE,15));c.setElevation(dp(1));
        LinearLayout title=h();title.addView(tv(addr,12,INK,Typeface.BOLD),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));title.addView(statusBadge(status));c.addView(title);
        LinearLayout m1=h();m1.addView(metricTiny(money(contract),"договор"),weight());m1.addView(metricTiny(money(paid),"получено"),weight());c.addView(m1);
        LinearLayout m2=h();m2.addView(metricTiny(money(plan),"план поступлений"),weight());m2.addView(metricTiny(money(debt),"дебиторка"),weight());c.addView(m2);
        ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(100);pb.setProgress(progress);pb.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        c.addView(pb,lpMatch(dp(6),0));c.addView(tv(progress+"% готовности · Следующий платёж: "+next,9,debt>0?RED:MUTED,Typeface.NORMAL));
        LinearLayout.LayoutParams p=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);p.setMargins(0,0,0,dp(7));c.setLayoutParams(p);return c;
    }

    // ---------- surveys / engineers / managers ----------

    private void showSurveys(){
        beginScreen(true);appBar("Замеры","Статистика, поиск и реанимация");periodSelector();
        int converted=countConvertedSurveys(), stale14=0, stale21=0;for(SurveyItem x:surveys)if(!x.converted){if(x.daysSinceContact>14)stale14++;if(x.daysSinceContact>21)stale21++;}
        LinearLayout a=h();a.addView(kpiCard("▰","Замеров за месяц",String.valueOf(surveys.size()),"Общий счётчик",ORANGE,"kpi:surveys_all"),weight());a.addView(kpiCard("↪","В монтаж перешли",String.valueOf(converted),"Конверсия "+(surveys.isEmpty()?0:converted*100/surveys.size())+"%",GREEN,"kpi:surveys_to_install"),weightMarginLeft());content.addView(a);
        sectionTitle("Контроль базы замеров",null,null);content.addView(attention("!","Без движения более 14 дней",stale14+" замеров требуют решения",ORANGE,"kpi:survey_14"));content.addView(attention("◷","Без повторного касания более 21 дня",stale21+" замеров требуют прозвона",RED,"kpi:survey_21"));
        EditText search=edit("Последние 4 цифры телефона или клиент");content.addView(search,lpMatch(dp(48),8));LinearLayout list=v();content.addView(list);
        Runnable rebuild=()->{list.removeAllViews();String q=search.getText().toString().replaceAll("[^0-9А-Яа-яA-Za-z]","").toLowerCase(Locale.ROOT);int n=0;for(SurveyItem x:surveys){String phone=x.phone.replaceAll("[^0-9]","");boolean ok=q.isEmpty()||phone.endsWith(q)||x.client.toLowerCase(Locale.ROOT).contains(q);if(ok){list.addView(surveyCard(x));n++;}}if(n==0)list.addView(emptyState("Замер не найден","Проверьте последние 4 цифры телефона."));};search.addTextChangedListener(new SimpleWatcher(rebuild));rebuild.run();
    }

    private void showEngineers(){
        beginScreen(true);appBar("Инженеры","Объекты, ТЗ, загрузка и отклонения");
        LinearLayout top=h();top.addView(kpiCard("♟","Инженеров",String.valueOf(engineers.size()),"Справочник",BLUE,"kpi:engineers_count"),weight());top.addView(kpiCard("!","ТЗ требуют внимания","3","Открыть объекты",RED,"objects"),weightMarginLeft());content.addView(top);
        sectionTitle("Инженеры","+ Инженер",v->showNewEngineerDialog());for(EngineerItem e:engineers){LinearLayout c=v();c.setPadding(dp(10),dp(10),dp(10),dp(10));c.setBackground(round(cardBg(),15));c.addView(tv(e.name,14,INK,Typeface.BOLD));c.addView(tv(e.activeObjects+" активных · "+e.planned+" запланировано · "+e.overdue+" просрочка · "+e.noReport+" без отчёта",10,MUTED,Typeface.NORMAL));c.setOnClickListener(v->navigate("engineer:"+e.id));LinearLayout.LayoutParams p=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);p.setMargins(0,0,0,dp(7));content.addView(c,p);}
        sectionTitle("Главная функция",null,null);content.addView(infoRow("Техническое задание","Монтажники · зарплата · дни · объёмы · платежи"));content.addView(infoRow("Контроль дня","План/факт · фото · расходы · переносы"));
    }

    private void showManagers(){
        beginScreen(true);appBar("Менеджеры","Интерфейс подготовлен, функционал пока не активирован");periodSelector();
        LinearLayout r=h();r.addView(kpiCard("●●","Лиды",String.valueOf((int)periodValue(42)),"Демо-показатель",BLUE,null),weight());r.addView(kpiCard("▰","Замеры",String.valueOf((int)periodValue(20)),"Демо-показатель",ORANGE,null),weightMarginLeft());content.addView(r);
        spacer(8);
        LinearLayout r2=h();r2.addView(kpiCard("▣","Договоры",String.valueOf(contractCountForPeriod()),"Демо-показатель",GREEN,null),weight());r2.addView(kpiCard("↪","Конверсия","26%","Замер → объект",ORANGE,null),weightMarginLeft());content.addView(r2);
        sectionTitle("Планируемый функционал",null,null);
        content.addView(infoRow("Лиды","Клиенты · источники · статусы · CPL"));
        content.addView(infoRow("Замеры","Назначено · проведено · результат"));
        content.addView(infoRow("КП и договоры","Сумма · дата · статус · причина отказа"));
        content.addView(infoRow("Контроль движения","7 дней без движения → Требует внимания"));
        content.addView(attention("!","Раздел менеджера пока в проектировании","Изменения в Alpha 6 заблокированы до отдельного согласования интерфейса.",ORANGE,null));
    }

    // ---------- calendar / settings ----------

    private void showCalendar(){
        beginScreen(true);appBar("Календарь","План работ и загрузка монтажников");sectionTitle("Сентябрь 2026","День "+selectedCalendarDay,null);
        LinearLayout days=h();for(int d=8;d<=14;d++){final int day=d;boolean sel=d==selectedCalendarDay;TextView t=pillText(String.valueOf(d),12,sel?WHITE:INK,sel?GREEN:softBg());t.setGravity(Gravity.CENTER);t.setOnClickListener(v->{selectedCalendarDay=day;saveDemoState();showCalendar();});days.addView(t,new LinearLayout.LayoutParams(0,dp(42),1));}content.addView(days);
        sectionTitle("Загрузка по людям","Нажмите на строку",null);
        View s1=scheduleRow("Алексей Смирнов",selectedCalendarDay==12?"Мытищи, ул. Лесная, 12":"Пушкино, СНТ Берёзка","В работе",GREEN);s1.setOnClickListener(v->navigate("installer:INS-001"));content.addView(s1);
        View s2=scheduleRow("Илья Орлов",selectedCalendarDay==13?"Свободен":"Мытищи, ул. Лесная, 12",selectedCalendarDay==13?"Свободен":"В работе",selectedCalendarDay==13?ORANGE:GREEN);s2.setOnClickListener(v->navigate("installer:INS-002"));content.addView(s2);
        View s3=scheduleRow("Сергей Плотников","Выходной","Свободен",ORANGE);s3.setOnClickListener(v->navigate("installer:INS-003"));content.addView(s3);
        View s4=scheduleRow("Андрей Крылов","Королёв, ул. Полевая, 7","Запланирован",BLUE);s4.setOnClickListener(v->navigate("installer:INS-004"));content.addView(s4);
        content.addView(attention("!","Контроль конфликта","Двойное назначение одного монтажника на одну дату будет заблокировано.",RED,"kpi:calendar_conflict"));
    }

    private void showSettings(){
        beginScreen(true);appBar("Настройки","Профиль, справочники, приложение");
        LinearLayout profile=h();TextView a=pillText(initials(leaderName),16,WHITE,GREEN_DARK);a.setGravity(Gravity.CENTER);profile.addView(a,new LinearLayout.LayoutParams(dp(54),dp(54)));LinearLayout p=v();p.setPadding(dp(10),0,0,0);p.addView(tv(leaderName,15,INK,Typeface.BOLD));p.addView(tv(demoRole+" · ADMIN",11,MUTED,Typeface.NORMAL));profile.addView(p,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));profile.setOnClickListener(v->editProfileDialog());content.addView(profile);
        sectionTitle("Интерфейс",null,null);content.addView(clickableInfoRow("Профиль","Изменить имя / фото",v->editProfileDialog()));content.addView(clickableInfoRow("Тема",themeMode,v->themeDialog()));content.addView(clickableInfoRow("Демо роли",demoRole,v->roleDialog()));content.addView(clickableInfoRow("Уведомления","Объекты · ТЗ · платежи · отчёты",v->navigate("kpi:notifications")));
        sectionTitle("Система",null,null);content.addView(clickableInfoRow("Справочники","Виды работ · статьи · статусы · причины",v->showDirectoriesDialog()));content.addView(clickableInfoRow("Пользователи и роли","Роль назначает руководитель",v->navigate("newEmployee")));content.addView(clickableInfoRow("Синхронизация","Последняя: демо · локальная база",v->showSyncDialog()));content.addView(infoRow("Версия","6.0.0-alpha6 · Full Demo"));content.addView(infoRow("Безопасная зона","Контент не заезжает под системные панели Android"));
        spacer(10);Button sync=primary("Синхронизировать");sync.setOnClickListener(v->showSyncDialog());content.addView(sync);spacer(7);Button reset=primaryOutline("Сбросить демо-данные");reset.setOnClickListener(v->confirmReset());content.addView(reset);
    }

    // ---------- KPI drilldown ----------

    private void showKpiDetail(String key){
        beginScreen(true);String title=humanKpi(key);appBar(title,"Детализация показателя · "+currentPeriod);content.addView(kpiCard("▥",title,kpiValue(key),"Каждая строка ведёт к первичной записи",GREEN,null));sectionTitle("Расшифровка",null,null);
        switch(key){
            case "turnover":
                addThreeMini(miniCard("₽","Общий оборот",money(actualTurnover()),GREEN,"kpi:turnover_all"),miniCard("↪","Промежуточные",money(intermediateTurnover()),ORANGE,"kpi:turnover_intermediate"),miniCard("✓","Закрытые объекты",money(closedTurnover()),BLUE,"kpi:turnover_closed"));
                sectionTitle("Последние поступления",null,null);for(MoneyTx t:txs)if(t.type.equals("INCOME"))content.addView(clickableInfoRow(t.sub,"+"+money(t.amount),v->openObjectByAddress(t.sub)));break;
            case "turnover_all":case "turnover_intermediate":case "turnover_closed":
                for(MoneyTx t:txs)if(t.type.equals("INCOME") && turnoverMatches(key,t))content.addView(clickableInfoRow(t.sub,t.title+" · "+money(t.amount),v->openObjectByAddress(t.sub)));break;
            case "profit":
                content.addView(infoRow("Оборот",money(actualTurnover())));content.addView(infoRow("Расходы",money(actualExpenses())));content.addView(infoRow("Операционная прибыль",money(Math.max(0,actualTurnover()-actualExpenses()))));content.addView(clickableInfoRow("Прибыль по закрытым объектам","Открыть объекты",v->navigate("objects")));break;
            case "planned_objects":
                content.addView(infoRow("Запланированы / не подтверждены",String.valueOf(countStatus("Запланирован"))));content.addView(infoRow("Подтверждены клиентом",String.valueOf(countStatus("Подтверждён клиентом"))));for(ObjectItem o:objects)if(o.status.equals("Запланирован")||o.status.equals("Подтверждён клиентом"))content.addView(clickableInfoRow(o.address,o.status,v->navigate("object:"+o.id)));break;
            case "objects_work":
                for(ObjectItem o:objects)if(o.status.equals("В работе"))content.addView(clickableInfoRow(o.address,o.progress+"% · "+o.installers,v->navigate("object:"+o.id)));break;
            case "plan_income":
                content.addView(infoRow("14 сентября","600 000 ₽"));content.addView(clickableInfoRow("Мытищи, ул. Лесная, 12","350 000 ₽ · этап ТЗ",v->navigate("payments:OBJ-001")));content.addView(clickableInfoRow("Пушкино, СНТ Берёзка","250 000 ₽ · этап ТЗ",v->navigate("payments:OBJ-003")));content.addView(infoRow("16 сентября","420 000 ₽"));content.addView(infoRow("20 сентября","700 000 ₽"));break;
            case "debt":
                content.addView(attention("!","Королёв, ул. Полевая, 7","Просрочено 200 000 ₽ · 4 дня",RED,"payments:OBJ-002"));content.addView(infoRow("Правило","Дебиторка = только просроченный неоплаченный остаток этапов ТЗ."));break;
            case "contracts":
                content.addView(infoRow("Логика","Объекты в работе + завершённые в выбранном периоде"));for(ObjectItem o:objects)if(o.status.equals("В работе")||o.status.equals("Завершён")||o.status.equals("Закрыт 100%"))content.addView(clickableInfoRow(o.address,money(o.contract),v->navigate("object:"+o.id)));break;
            case "avg":
                content.addView(infoRow("Общий средний чек",money(averageCheck())));content.addView(infoRow("Утепление","340 000 ₽"));content.addView(infoRow("Фасады","520 000 ₽"));content.addView(infoRow("Кровля","610 000 ₽"));content.addView(infoRow("Комплексное утепление","780 000 ₽"));sectionTitle("Утепление — детализация",null,null);for(String x:new String[]{"Пол — 210 000 ₽","Стены — 290 000 ₽","Перекрытие — 240 000 ₽","Кровля — 360 000 ₽","Стены + пол — 430 000 ₽","Крыша + стены — 510 000 ₽","Пол + стены + крыша — 690 000 ₽"})content.addView(infoRow("Направление",x));break;
            case "leads":for(String x:new String[]{"Яндекс Директ — 18","Авито — 9","Telegram — 5","VK — 4","YouTube — 3","Рекомендации — 3"})content.addView(infoRow("Источник",x));content.addView(attention("!","Лиды сегодня не обновлены","Требуется ежедневное обновление SMM",ORANGE,null));break;
            case "notifications":content.addView(attention("◷","Просроченный платёж","200 000 ₽ · Королёв",RED,"payments:OBJ-002"));content.addView(attention("▰","Замеры сегодня не обновлены","Проверить инженера",ORANGE,"surveys"));content.addView(attention("●","Лиды сегодня не обновлены","Проверить SMM",ORANGE,"kpi:leads"));content.addView(attention("▤","Подтверждённый объект без полного ТЗ","Ивантеевка",RED,"tech:OBJ-004"));break;
            case "expense_object":moneyDialog("EXPENSE");content.addView(infoRow("Действие","Форма расхода открыта"));break;
            default:content.addView(infoRow("Показатель",title));content.addView(clickableInfoRow("Открыть связанный раздел","Перейти",v->navigate(defaultTargetForKpi(key))));
        }
    }

    // ---------- helpers ----------

    private String humanKpi(String k){
        Map<String,String> m=new HashMap<>();
        m.put("turnover","Оборот");m.put("turnover_all","Общий оборот");m.put("turnover_intermediate","Промежуточные платежи");m.put("turnover_closed","Оплаты по закрытым объектам");m.put("profit","Прибыль");m.put("objects_work","Объекты в работе");m.put("planned_objects","Запланированные объекты");m.put("leads","Лиды");
        m.put("contracts","Договоры");m.put("avg","Средний чек");m.put("debt","Дебиторка");m.put("expenses","Расходы");
        m.put("plan_income","Планируется поступление");m.put("remaining","Осталось получить");m.put("balance","Общий остаток");
        m.put("accountable","Подотчёт");m.put("notifications","Уведомления");return m.getOrDefault(k,k.replace('_',' '));
    }
    private String kpiValue(String k){
        if(k.equals("turnover")||k.equals("turnover_all"))return money(actualTurnover());if(k.equals("turnover_intermediate"))return money(intermediateTurnover());if(k.equals("turnover_closed"))return money(closedTurnover());if(k.equals("profit"))return money(Math.max(0,actualTurnover()-actualExpenses()));if(k.equals("debt"))return money(debtTotal());
        if(k.equals("plan_income"))return money(plannedReceiptsTotal());if(k.equals("remaining"))return money(remainingReceiptsTotal());if(k.equals("leads"))return "42";if(k.equals("contracts"))return String.valueOf(contractCountForPeriod());if(k.equals("avg"))return money(averageCheck());if(k.equals("planned_objects"))return String.valueOf(countStatus("Запланирован")+countStatus("Подтверждён клиентом"));if(k.equals("objects_work"))return String.valueOf(countStatus("В работе"));return "Подробнее";
    }

    private ObjectItem findObject(String id){for(ObjectItem o:objects)if(o.id.equals(id))return o;return null;}
    private InstallerItem findInstaller(String id){for(InstallerItem i:installers)if(i.id.equals(id))return i;return null;}

    private LinearLayout infoHero(String title,String status,int progress){
        LinearLayout c=v();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(round(Color.rgb(239,251,247),18));
        c.addView(tv(title,18,INK,Typeface.BOLD));c.addView(statusBadge(status));
        if(progress>0){
            ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(100);pb.setProgress(progress);pb.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
            c.addView(pb,lpMatch(dp(7),0));c.addView(tv("Готовность "+progress+"%",10,GREEN,Typeface.BOLD));
        }
        return c;
    }

    private View infoRow(String left,String right){
        LinearLayout r=h();r.setPadding(dp(10),dp(9),dp(10),dp(9));r.setGravity(Gravity.CENTER_VERTICAL);r.setBackground(round(WHITE,13));
        r.addView(tv(left,11,MUTED,Typeface.NORMAL),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView v=tv(right,11,INK,Typeface.BOLD);v.setGravity(Gravity.RIGHT);r.addView(v,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        LinearLayout.LayoutParams p=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);p.setMargins(0,0,0,dp(5));r.setLayoutParams(p);return r;
    }

    private View metricTiny(String value,String label){
        LinearLayout b=v();b.setPadding(dp(4),dp(5),dp(4),dp(5));b.addView(tv(value,11,INK,Typeface.BOLD));b.addView(tv(label,8,MUTED,Typeface.NORMAL));return b;
    }

    private View scheduleRow(String name,String object,String status,int tint){
        LinearLayout r=h();r.setPadding(dp(9),dp(9),dp(9),dp(9));r.setBackground(round(WHITE,14));
        TextView a=pillText(initials(name),12,WHITE,tint);a.setGravity(Gravity.CENTER);r.addView(a,new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout x=v();x.setPadding(dp(9),0,0,0);x.addView(tv(name,11,INK,Typeface.BOLD));x.addView(tv(object,9,MUTED,Typeface.NORMAL));r.addView(x,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));r.addView(statusBadge(status));
        LinearLayout.LayoutParams p=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);p.setMargins(0,0,0,dp(6));r.setLayoutParams(p);return r;
    }

    private TextView statusBadge(String status){
        int color=status.contains("работ")?GREEN:status.contains("Заплан")?BLUE:status.contains("Подтверж")?ORANGE:status.contains("Выход")?ORANGE:GREEN;
        TextView b=pillText(status,9,color,light(color));b.setPadding(dp(8),dp(5),dp(8),dp(5));return b;
    }

    private EditText field(String label,String value){
        TextView l=tv(label,10,MUTED,Typeface.BOLD);LinearLayout.LayoutParams lp=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);lp.setMargins(0,dp(9),0,dp(4));content.addView(l,lp);
        EditText e=edit(label);e.setText(value);content.addView(e,lpMatch(dp(50),0));return e;
    }

    private EditText edit(String hint){
        EditText e=new EditText(this);e.setTextSize(13);e.setTextColor(INK);e.setHintTextColor(Color.rgb(150,160,174));e.setSingleLine(true);e.setHint(hint);e.setPadding(dp(12),0,dp(12),0);e.setBackground(round(Color.rgb(250,251,253),13));return e;
    }

    private Button primary(String text){
        Button b=new Button(this);b.setText(text);b.setTextSize(12);b.setTextColor(WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(round(GREEN,14));b.setMinHeight(dp(50));return b;
    }
    private Button primaryOutline(String text){
        Button b=new Button(this);b.setText(text);b.setTextSize(12);b.setTextColor(GREEN);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);GradientDrawable g=round(WHITE,14);g.setStroke(dp(1),GREEN);b.setBackground(g);b.setMinHeight(dp(50));return b;
    }
    private Button smallButton(String text,int color){
        Button b=new Button(this);b.setText(text);b.setTextSize(10);b.setTextColor(color);b.setAllCaps(false);GradientDrawable g=round(WHITE,13);g.setStroke(dp(1),light(color));b.setBackground(g);return b;
    }

    private void addThreeMini(View a,View b,View c){
        LinearLayout r=h();r.addView(a,weight());r.addView(b,weightMarginLeft());r.addView(c,weightMarginLeft());content.addView(r);
    }
    private void addFourQuick(View a,View b,View c,View d){
        LinearLayout r=h();r.addView(a,weight());r.addView(b,weightMarginLeft());r.addView(c,weightMarginLeft());r.addView(d,weightMarginLeft());content.addView(r);
    }

    private LinearLayout h(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout v(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView tv(String s,int sp,int color,int style){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(resolveText(color));t.setTypeface(Typeface.DEFAULT,style);t.setLineSpacing(0,1.05f);return t;}
    private TextView pillText(String s,int sp,int color,int bg){TextView t=tv(s,sp,color,Typeface.BOLD);t.setBackground(round(bg,999));return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(resolveBg(color));g.setCornerRadius(dp(radius));return g;}
    private int light(int color){
        int r=Color.red(color),g=Color.green(color),b=Color.blue(color);
        return Color.rgb((r+255*6)/7,(g+255*6)/7,(b+255*6)/7);
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private LinearLayout.LayoutParams lpMatch(int height,int marginTop){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,height);if(marginTop>0)p.setMargins(0,dp(marginTop),0,0);return p;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);}
    private LinearLayout.LayoutParams weightMarginLeft(){LinearLayout.LayoutParams p=weight();p.setMargins(dp(7),0,0,0);return p;}
    private void spacer(int d){Space s=new Space(this);content.addView(s,new LinearLayout.LayoutParams(1,dp(d)));}
    private String money(long v){return NumberFormat.getNumberInstance(new Locale("ru","RU")).format(v)+" ₽";}
    private long parseLong(String s){try{return Long.parseLong(s.replaceAll("[^0-9]",""));}catch(Exception e){return 0;}}
    private String initials(String name){String[] p=name.trim().split("\\s+");return p.length>1?(""+p[0].charAt(0)+p[1].charAt(0)).toUpperCase():"ТК";}

    // ---------- full demo interactions ----------

    private int screenBg(){return "Тёмная".equals(themeMode)?Color.rgb(18,24,33):WHITE;}
    private int cardBg(){return "Тёмная".equals(themeMode)?Color.rgb(29,37,48):WHITE;}
    private int softBg(){return "Тёмная".equals(themeMode)?Color.rgb(38,47,59):Color.rgb(246,248,251);}
    private int resolveText(int c){if(!"Тёмная".equals(themeMode))return c;if(c==INK)return Color.rgb(239,244,250);if(c==MUTED)return Color.rgb(170,182,198);return c;}
    private int resolveBg(int c){if(!"Тёмная".equals(themeMode))return c;if(c==WHITE)return cardBg();if(c==Color.rgb(246,248,251)||c==Color.rgb(243,246,249)||c==Color.rgb(250,251,253))return softBg();return c;}
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();}

    private long periodValue(long monthValue){
        switch(currentPeriod){case "Сегодня":return Math.max(1,monthValue/22);case "Неделя":return Math.max(1,monthValue/4);case "Квартал":return monthValue*3;case "Год":return monthValue*12;default:return monthValue;}
    }
    private String periodDelta(String d){return ("Месяц".equals(currentPeriod)?"↑ "+d:"Демо · "+currentPeriod);}
    private void showPeriodDialog(){String[] ps={"Сегодня","Неделя","Месяц","Квартал","Год"};new AlertDialog.Builder(this).setTitle("Период").setSingleChoiceItems(ps,Arrays.asList(ps).indexOf(currentPeriod),(d,w)->{currentPeriod=ps[w];d.dismiss();saveDemoState();render();}).show();}

    private void showQuickAddDialog(){
        String[] a={"Создать объект","Добавить приход","Добавить расход","Передать деньги","Добавить сотрудника","Новый замер"};
        new AlertDialog.Builder(this).setTitle("Добавить").setItems(a,(d,w)->{switch(w){case 0:navigate("create");break;case 1:moneyDialog("INCOME");break;case 2:moneyDialog("EXPENSE");break;case 3:transferDialog();break;case 4:navigate("newEmployee");break;case 5:newSurveyDialog();break;}}).show();
    }
    private void showScreenMenu(){String[] a={"На главную","Обновить экран","Поделиться сводкой","О приложении"};new AlertDialog.Builder(this).setTitle("Меню").setItems(a,(d,w)->{if(w==0)navigate("main");else if(w==1)render();else if(w==2)shareSummary();else showAboutDialog();}).show();}
    private void showAboutDialog(){new AlertDialog.Builder(this).setTitle("Тёплая Компания 4.0").setMessage("Alpha 6 Full Demo\n\nНативное Android-приложение. Все разделы работают на локальных демо-данных. Google Sheets пока не подключена.").setPositiveButton("Понятно",null).show();}
    private void shareSummary(){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,"Тёплая Компания — демо-сводка\nОборот: "+money(periodValue(1245000))+"\nПрибыль: "+money(periodValue(425000))+"\nОбъекты в работе: "+countStatus("В работе")+"\nДебиторка: 320 000 ₽");startActivity(Intent.createChooser(i,"Поделиться сводкой"));}

    private int countStatus(String status){int n=0;for(ObjectItem o:objects)if(o.status.equals(status))n++;return n;}
    private int countInstallerStatus(String st){int n=0;for(InstallerItem i:installers)if(i.status.equals(st))n++;return n;}
    private int totalClosedObjects(){int n=0;for(InstallerItem i:installers)n+=i.closedObjects;return n;}
    private int countConvertedSurveys(){int n=0;for(SurveyItem s:surveys)if(s.converted)n++;return n;}
    private String[] engineerNames(){String[] a=new String[engineers.size()];for(int i=0;i<a.length;i++)a[i]=engineers.get(i).name;return a;}
    private String[] managerNames(){String[] a=new String[managers.size()];for(int i=0;i<a.length;i++)a[i]=managers.get(i).name;return a;}

    private EditText choiceField(String label,String value,String[] choices){EditText e=field(label,value);e.setFocusable(false);e.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(label).setItems(choices,(d,w)->e.setText(choices[w])).show());return e;}
    private EditText taskField(String label,String value){TextView l=tv(label,10,MUTED,Typeface.BOLD);LinearLayout.LayoutParams lp=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);lp.setMargins(0,dp(7),0,dp(3));content.addView(l,lp);EditText e=edit(label);e.setText(value);content.addView(e,lpMatch(dp(48),0));return e;}
    private View emptyState(String title,String sub){LinearLayout c=v();c.setPadding(dp(16),dp(18),dp(16),dp(18));c.setGravity(Gravity.CENTER);c.setBackground(round(softBg(),15));c.addView(tv(title,14,INK,Typeface.BOLD));c.addView(tv(sub,10,MUTED,Typeface.NORMAL));return c;}
    private View clickableInfoRow(String left,String right,View.OnClickListener click){View r=infoRow(left,right);r.setOnClickListener(click);return r;}
    private View labelWrap(String label,View view){LinearLayout l=v();l.setPadding(0,dp(5),0,dp(5));l.addView(tv(label,10,MUTED,Typeface.BOLD));l.addView(view,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));return l;}
    private String joinNames(Collection<String> c){StringBuilder b=new StringBuilder();for(String x:c){if(b.length()>0)b.append(", ");b.append(x);}return b.toString();}

    private void dial(String phone){try{startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+phone)));}catch(Exception e){toast("Набор номера недоступен");}}
    private void openMap(String address){try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(address)));startActivity(i);}catch(Exception e){toast("Карты не найдены");}}
    private void changeObjectStatus(ObjectItem o){String[] st={"Подтверждён клиентом","Запланирован","В работе","Приостановлен","Завершён","Закрыт 100%"};new AlertDialog.Builder(this).setTitle("Статус объекта").setSingleChoiceItems(st,Arrays.asList(st).indexOf(o.status),(d,w)->{o.status=st[w];if(o.status.equals("Завершён")||o.status.equals("Закрыт 100%"))o.progress=100;saveDemoState();d.dismiss();showObjectDetail(o.id);}).show();}

    private void showPayments(String objectId){
        ObjectItem o=findObject(objectId);if(o==null){navigate("objects");return;}beginScreen(true);appBar("График платежей",o.address);long rem=Math.max(0,o.contract-o.paid);
        content.addView(kpiCard("↓","Получено",money(o.paid),"Фактический оборот по объекту",GREEN,null));spacer(7);content.addView(kpiCard("◷","Осталось",money(rem),"По договору",ORANGE,null));
        sectionTitle("Этапы оплат","Демо",null);TechTask t=techTasks.get(o.id);long p1=t==null?Math.min(rem,200000):t.pay1,p2=t==null?Math.min(Math.max(0,rem-p1),180000):t.pay2,p3=t==null?Math.max(0,rem-p1-p2):t.pay3;
        content.addView(paymentRow(o,"25.09.2026",p1,false));content.addView(paymentRow(o,"27.09.2026",p2,false));content.addView(paymentRow(o,"30.09.2026",p3,false));
        if(o.id.equals("OBJ-002"))content.addView(attention("!","Просрочено 200 000 ₽","4 дня · ответственный менеджер: "+o.manager,RED,null));
    }
    private View paymentRow(ObjectItem o,String date,long amount,boolean paid){LinearLayout r=h();r.setPadding(dp(10),dp(9),dp(10),dp(9));r.setBackground(round(cardBg(),14));LinearLayout x=v();x.addView(tv(date,11,INK,Typeface.BOLD));x.addView(tv(paid?"Оплачен":"Ожидается",9,paid?GREEN:MUTED,Typeface.NORMAL));r.addView(x,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView m=tv(money(amount),12,INK,Typeface.BOLD);r.addView(m);if(!paid&&amount>0){TextView ok=pillText("+ Оплата",9,WHITE,GREEN);ok.setPadding(dp(8),dp(5),dp(8),dp(5));ok.setOnClickListener(v->{o.paid=Math.min(o.contract,o.paid+amount);txs.add(0,new MoneyTx("INCOME","Оплата этапа",o.address,amount));igorBalance+=amount;saveDemoState();toast("Платёж проведён");showPayments(o.id);});LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(34));op.setMargins(dp(8),0,0,0);r.addView(ok,op);}LinearLayout.LayoutParams p=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);p.setMargins(0,0,0,dp(6));r.setLayoutParams(p);return r;}

    private void showPhotoReports(String objectId){ObjectItem o=findObject(objectId);if(o==null){navigate("objects");return;}beginScreen(true);appBar("Фотоотчёты",o.address);sectionTitle("День 1","Фото ДО · процесс · ПОСЛЕ",null);addPhotoDemo("ДО","Фасад до начала работ");addPhotoDemo("ПРОЦЕСС","Монтаж каркаса, 85 м²");addPhotoDemo("ПОСЛЕ","Участок завершён и проверен");sectionTitle("Отчёт монтажника",null,null);content.addView(infoRow("Выполнено","Каркас 85 м² · мембрана 80 м²"));content.addView(infoRow("Время","08:12 — 18:05"));content.addView(infoRow("Комментарий","Работы идут по плану"));Button add=primaryOutline("+ Добавить демо-фотоотчёт");add.setOnClickListener(v->toast("Фотоотчёт добавлен в демо-ленту"));content.addView(add);}
    private void addPhotoDemo(String stage,String caption){LinearLayout c=h();c.setPadding(dp(10),dp(10),dp(10),dp(10));c.setBackground(round(softBg(),14));TextView ph=pillText("▧",26,BLUE,light(BLUE));ph.setGravity(Gravity.CENTER);c.addView(ph,new LinearLayout.LayoutParams(dp(66),dp(66)));LinearLayout t=v();t.setPadding(dp(10),0,0,0);t.addView(tv(stage,11,BLUE,Typeface.BOLD));t.addView(tv(caption,11,INK,Typeface.NORMAL));t.addView(tv("12.09.2026 · монтажник",9,MUTED,Typeface.NORMAL));c.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));LinearLayout.LayoutParams p=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);p.setMargins(0,0,0,dp(7));content.addView(c,p);}

    private void installerPaymentDialog(InstallerItem i){EditText amt=edit("Сумма аванса / выплаты");amt.setInputType(InputType.TYPE_CLASS_NUMBER);new AlertDialog.Builder(this).setTitle(i.name).setView(amt).setPositiveButton("Выдать",(d,w)->{long v=parseLong(amt.getText().toString());if(v>0){i.paid+=v;igorBalance-=v;txs.add(0,new MoneyTx("EXPENSE","Выплата монтажнику",i.name,v));saveDemoState();showInstallerDetail(i.id);}}).setNegativeButton("Отмена",null).show();}
    private void showNewInstallerDialog(){EditText name=edit("ФИО монтажника");new AlertDialog.Builder(this).setTitle("Новый монтажник").setView(name).setPositiveButton("Добавить",(d,w)->{String n=name.getText().toString().trim();if(!n.isEmpty()){installers.add(new InstallerItem("INS-"+(installers.size()+1),n,0,0,0,0,0,0,"Не выдавалась","Свободен"));saveDemoState();showInstallers();}}).setNegativeButton("Отмена",null).show();}

    private boolean financeMatches(MoneyTx t){if(financeFilter.equals("Все"))return true;if(financeFilter.equals("Приходы"))return t.type.equals("INCOME");if(financeFilter.equals("Расходы"))return t.type.equals("EXPENSE");if(financeFilter.equals("Переводы"))return t.type.equals("TRANSFER");if(financeFilter.equals("Маркетинг"))return t.sub.contains("Маркетинг")||t.title.contains("Директ")||t.title.contains("Реклама");if(financeFilter.equals("Монтажники"))return t.sub.contains("монтаж")||findInstallerByName(t.sub)!=null;return !t.sub.equals("Без привязки");}
    private boolean analyticsObjectMatches(ObjectItem o){if(analyticsFilter.equals("Все объекты")||analyticsFilter.equals("По инженеру")||analyticsFilter.equals("По менеджеру"))return true;return o.status.equals(analyticsFilter);}

    private InstallerItem findInstallerByName(String n){for(InstallerItem i:installers)if(i.name.equals(n))return i;return null;}
    private SurveyItem findSurvey(String id){for(SurveyItem s:surveys)if(s.id.equals(id))return s;return null;}
    private EngineerItem findEngineer(String id){for(EngineerItem e:engineers)if(e.id.equals(id))return e;return null;}
    private ManagerItem findManager(String id){for(ManagerItem m:managers)if(m.id.equals(id))return m;return null;}

    private View surveyCard(SurveyItem x){LinearLayout c=v();c.setPadding(dp(10),dp(9),dp(10),dp(9));c.setBackground(round(cardBg(),14));LinearLayout top=h();LinearLayout txt=v();txt.addView(tv(x.client,12,INK,Typeface.BOLD));txt.addView(tv(x.phone+" · замер "+x.date,9,MUTED,Typeface.NORMAL));top.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));top.addView(statusBadge(x.converted?"Переведён в объект":x.daysSinceContact>21?"Без касания 21+":x.daysSinceContact>14?"Без движения 14+":"В работе"));c.addView(top);c.addView(tv("Потенциал: "+money(x.potential)+" · последнее касание "+x.daysSinceContact+" дн. назад",9,MUTED,Typeface.NORMAL));c.setOnClickListener(v->navigate("survey:"+x.id));LinearLayout.LayoutParams p=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);p.setMargins(0,0,0,dp(6));c.setLayoutParams(p);return c;}
    private void showSurveyDetail(String id){SurveyItem x=findSurvey(id);if(x==null){navigate("surveys");return;}beginScreen(true);appBar("Замер",x.client);content.addView(infoRow("Телефон",x.phone));content.addView(infoRow("Дата замера",x.date));content.addView(infoRow("Потенциал договора",money(x.potential)));content.addView(infoRow("Последнее касание",x.daysSinceContact+" дней назад"));Button call=primaryOutline("Позвонить клиенту");call.setOnClickListener(v->{x.daysSinceContact=0;saveDemoState();dial(x.phone);});content.addView(call);spacer(7);Button conv=primary(x.converted?"Уже переведён в объект":"Перевести замер в объект");conv.setEnabled(!x.converted);conv.setOnClickListener(v->convertSurvey(x));content.addView(conv);}
    private void convertSurvey(SurveyItem x){String oid="OBJ-"+String.format(Locale.US,"%03d",objects.size()+1);ObjectItem o=new ObjectItem(oid,"Адрес из замера — уточнить",x.client,"Комплексное утепление","Подтверждён клиентом",0,x.potential,0,"Константин","Игорь Игоревич","Не назначены");objects.add(0,o);x.converted=true;saveDemoState();toast("Замер переведён в объект");navigate("object:"+oid);}
    private void newSurveyDialog(){LinearLayout f=v();f.setPadding(dp(18),0,dp(18),0);EditText n=edit("Клиент");EditText ph=edit("Телефон");f.addView(labelWrap("Клиент",n));f.addView(labelWrap("Телефон",ph));new AlertDialog.Builder(this).setTitle("Новый замер").setView(f).setPositiveButton("Добавить",(d,w)->{if(!n.getText().toString().trim().isEmpty()){surveys.add(0,new SurveyItem("Z-"+(100+surveys.size()+1),n.getText().toString(),ph.getText().toString(),"12.09.2026",0,300000,false));saveDemoState();navigate("surveys");}}).setNegativeButton("Отмена",null).show();}

    private void showEngineerDetail(String id){EngineerItem e=findEngineer(id);if(e==null){navigate("engineers");return;}beginScreen(true);appBar(e.name,"Инженер");content.addView(infoRow("Активные объекты",String.valueOf(e.activeObjects)));content.addView(infoRow("Запланированные",String.valueOf(e.planned)));content.addView(infoRow("Просрочки",String.valueOf(e.overdue)));content.addView(infoRow("Без отчёта",String.valueOf(e.noReport)));sectionTitle("Объекты инженера",null,null);for(ObjectItem o:objects)if(o.engineer.equals(e.name))content.addView(clickableInfoRow(o.address,o.status,v->navigate("object:"+o.id)));Button open=primary("Создать/открыть ТЗ");open.setOnClickListener(v->navigate("objects"));content.addView(open);}
    private void showManagerDetail(String id){ManagerItem m=findManager(id);if(m==null){navigate("managers");return;}beginScreen(true);appBar(m.name,"Менеджер");content.addView(infoRow("Лиды",String.valueOf(m.leads)));content.addView(infoRow("Замеры",String.valueOf(m.surveys)));content.addView(infoRow("Договоры",String.valueOf(m.contracts)));content.addView(infoRow("Монтажи",String.valueOf(m.installations)));content.addView(infoRow("Конверсия лид → договор",m.leads==0?"0%":(m.contracts*100/m.leads)+"%"));Button lead=primaryOutline("+ Новая заявка");lead.setOnClickListener(v->newLeadDialog());content.addView(lead);}
    private void showNewEngineerDialog(){EditText n=edit("Имя инженера");new AlertDialog.Builder(this).setTitle("Добавить инженера").setView(n).setPositiveButton("Добавить",(d,w)->{if(!n.getText().toString().trim().isEmpty()){engineers.add(new EngineerItem("ENG-"+(engineers.size()+1),n.getText().toString(),0,0,0,0));saveDemoState();showEngineers();}}).setNegativeButton("Отмена",null).show();}
    private void showNewManagerDialog(){EditText n=edit("Имя менеджера");new AlertDialog.Builder(this).setTitle("Добавить менеджера").setView(n).setPositiveButton("Добавить",(d,w)->{if(!n.getText().toString().trim().isEmpty()){managers.add(new ManagerItem("MGR-"+(managers.size()+1),n.getText().toString(),0,0,0,0));saveDemoState();showManagers();}}).setNegativeButton("Отмена",null).show();}
    private void newLeadDialog(){EditText n=edit("Клиент / телефон");new AlertDialog.Builder(this).setTitle("Новая заявка").setView(n).setPositiveButton("Сохранить",(d,w)->{if(!managers.isEmpty())managers.get(0).leads++;saveDemoState();toast("Заявка добавлена в демо-воронку");}).setNegativeButton("Отмена",null).show();}

    private void showNewEmployee(){beginScreen(true);appBar("Новый сотрудник","Руководитель назначает роль");final EditText n=field("ФИО *","Новый сотрудник");final EditText phone=field("Телефон","+7 900 000-00-00");final EditText role=choiceField("Роль","Монтажник",new String[]{"Монтажник","Инженер","Менеджер"});Button save=primary("Создать сотрудника");save.setOnClickListener(v->{String name=n.getText().toString().trim();if(name.isEmpty())return;if(role.getText().toString().equals("Монтажник"))installers.add(new InstallerItem("INS-"+(installers.size()+1),name,0,0,0,0,0,0,"Не выдавалась","Свободен"));else if(role.getText().toString().equals("Инженер"))engineers.add(new EngineerItem("ENG-"+(engineers.size()+1),name,0,0,0,0));else managers.add(new ManagerItem("MGR-"+(managers.size()+1),name,0,0,0,0));saveDemoState();toast("Сотрудник создан");navigate("settings");});content.addView(save);}

    private void showAccountable(String who){beginScreen(true);appBar("Подотчёт",who.equals("all")?"Все ответственные":who);if(who.equals("all")){content.addView(clickableInfoRow("Игорь",money(igorBalance),v->navigate("accountable:Игорь")));content.addView(clickableInfoRow("Константин",money(konstantinBalance),v->navigate("accountable:Константин")));content.addView(infoRow("Общий остаток",money(igorBalance+konstantinBalance)));return;}long bal=who.equals("Игорь")?igorBalance:konstantinBalance;content.addView(kpiCard("₽","Текущий остаток",money(bal),"Живой баланс",GREEN,null));sectionTitle("Последние операции",null,null);for(MoneyTx t:txs)if(t.title.contains(who)||t.sub.contains(who)||t.type.equals("TRANSFER"))content.addView(infoRow(t.title,money(t.amount)));Button move=primaryOutline("Передать деньги");move.setOnClickListener(v->transferDialog());content.addView(move);}

    private void editProfileDialog(){EditText n=edit("Имя");n.setText(leaderName);new AlertDialog.Builder(this).setTitle("Профиль руководителя").setView(n).setPositiveButton("Сохранить",(d,w)->{String x=n.getText().toString().trim();if(!x.isEmpty())leaderName=x;saveDemoState();showSettings();}).setNeutralButton("Фото",(d,w)->toast("В демо фото профиля отмечено как выбранное")).setNegativeButton("Отмена",null).show();}
    private void themeDialog(){String[] a={"Светлая","Тёмная","Как на устройстве"};new AlertDialog.Builder(this).setTitle("Тема приложения").setSingleChoiceItems(a,Arrays.asList(a).indexOf(themeMode),(d,w)->{themeMode=a[w];if(themeMode.equals("Как на устройстве"))themeMode="Светлая";saveDemoState();d.dismiss();configureSystemBars();render();}).show();}
    private void roleDialog(){String[] a={"Руководитель","Инженер","Менеджер","Монтажник"};new AlertDialog.Builder(this).setTitle("Демо интерфейса роли").setItems(a,(d,w)->{demoRole=a[w];saveDemoState();if(demoRole.equals("Инженер"))navigate("engineers");else if(demoRole.equals("Менеджер"))navigate("managers");else if(demoRole.equals("Монтажник"))navigate("installers");else navigate("main");}).show();}
    private void showDirectoriesDialog(){String[] a={"Виды работ","Статьи расходов","Статусы объектов","Источники рекламы","Причины переноса","Причины отказа","Виды оплат"};new AlertDialog.Builder(this).setTitle("Справочники").setItems(a,(d,w)->new AlertDialog.Builder(this).setTitle(a[w]).setMessage("Демо-справочник открыт. В рабочей версии значения будут редактироваться без изменения кода.").setPositiveButton("Добавить значение",(x,y)->toast("Демо-значение добавлено")).setNegativeButton("Закрыть",null).show()).show();}
    private void confirmReset(){new AlertDialog.Builder(this).setTitle("Сбросить демо?").setMessage("Все добавленные локально объекты, операции и сотрудники вернутся к исходным примерам.").setPositiveButton("Сбросить",(d,w)->{seedDemoData();saveDemoState();navigate("main");}).setNegativeButton("Отмена",null).show();}

    private String defaultTargetForKpi(String key){if(key.contains("installer")||key.contains("payroll")||key.contains("accrued"))return "installers";if(key.contains("survey"))return "surveys";if(key.contains("object")||key.contains("contract"))return "objects";if(key.contains("expense")||key.contains("balance")||key.contains("accountable"))return "finance";return "analytics";}
    private String kpiValueExtended(String key){return kpiValue(key);}

    private long actualTurnover(){long v=0;for(MoneyTx t:txs)if("INCOME".equals(t.type))v+=t.amount;return v;}
    private long actualExpenses(){long v=0;for(MoneyTx t:txs)if("EXPENSE".equals(t.type))v+=t.amount;return v;}
    private long remainingReceiptsTotal(){long v=0;for(ObjectItem o:objects)if(!o.status.equals("Закрыт 100%"))v+=Math.max(0,o.contract-o.paid);return v;}
    private long plannedReceiptsTotal(){long remaining=remainingReceiptsTotal();long debt=debtTotal();return Math.max(0,remaining-debt);}
    private long debtTotal(){return 200000L;}
    private int contractCountForPeriod(){int n=0;for(ObjectItem o:objects)if(o.status.equals("В работе")||o.status.equals("Завершён")||o.status.equals("Закрыт 100%"))n++;return n;}
    private long averageCheck(){long sum=0;int n=0;for(ObjectItem o:objects)if(o.contract>0){sum+=o.contract;n++;}return n==0?0:sum/n;}
    private long intermediateTurnover(){long v=0;for(MoneyTx t:txs)if("INCOME".equals(t.type)&&t.title.contains("Промежуточ"))v+=t.amount;return v;}
    private long closedTurnover(){long v=0;for(MoneyTx t:txs)if("INCOME".equals(t.type)&&t.title.contains("Финальный"))v+=t.amount;return v;}
    private boolean turnoverMatches(String key,MoneyTx t){if(key.equals("turnover_all"))return true;if(key.equals("turnover_intermediate"))return t.title.contains("Промежуточ");if(key.equals("turnover_closed"))return t.title.contains("Финальный");return false;}
    private void openObjectByAddress(String address){for(ObjectItem o:objects)if(address.contains(o.address)||o.address.contains(address)){navigate("object:"+o.id);return;}navigate("finance");}
    private void showSyncDialog(){new AlertDialog.Builder(this).setTitle("Синхронизация").setMessage("ДЕМО-РЕЖИМ\n\nСейчас данные сохраняются локально на телефоне. Контракт синхронизации уже зафиксирован: Приложение ⇄ Google Apps Script API ⇄ Google Sheets.\n\nПри подключении кнопка будет отправлять локальные изменения, получать изменения таблицы и показывать время последней успешной синхронизации.").setPositiveButton("Демо-синхронизация",(d,w)->toast("Демо: данные синхронизированы")).setNegativeButton("Закрыть",null).show();}

    private void loadDemoState(){
        themeMode=prefs.getString("theme","Светлая");leaderName=prefs.getString("leader","Игорь Игоревич");demoRole=prefs.getString("role","Руководитель");currentPeriod=prefs.getString("period","Месяц");selectedCalendarDay=prefs.getInt("calDay",12);
        String json=prefs.getString("state",null);if(json==null){seedDemoData();return;}
        try{JSONObject root=new JSONObject(json);igorBalance=root.optLong("igor",420000);konstantinBalance=root.optLong("konstantin",386000);objects.clear();installers.clear();txs.clear();surveys.clear();engineers.clear();managers.clear();techTasks.clear();
            JSONArray oa=root.optJSONArray("objects");if(oa!=null)for(int i=0;i<oa.length();i++){JSONObject o=oa.getJSONObject(i);objects.add(new ObjectItem(o.getString("id"),o.getString("address"),o.getString("client"),o.getString("workType"),o.getString("status"),o.getInt("progress"),o.getLong("contract"),o.getLong("paid"),o.getString("engineer"),o.getString("manager"),o.getString("installers")));}
            JSONArray ia=root.optJSONArray("installers");if(ia!=null)for(int i=0;i<ia.length();i++){JSONObject o=ia.getJSONObject(i);installers.add(new InstallerItem(o.getString("id"),o.getString("name"),o.getInt("workDays"),o.getInt("daysOff"),o.getInt("closed"),o.getLong("accrued"),o.getLong("paid"),o.getInt("tools"),o.getString("uniform"),o.getString("status")));}
            JSONArray ta=root.optJSONArray("txs");if(ta!=null)for(int i=0;i<ta.length();i++){JSONObject o=ta.getJSONObject(i);txs.add(new MoneyTx(o.getString("type"),o.getString("title"),o.getString("sub"),o.getLong("amount")));}
            JSONArray sa=root.optJSONArray("surveys");if(sa!=null)for(int i=0;i<sa.length();i++){JSONObject o=sa.getJSONObject(i);surveys.add(new SurveyItem(o.getString("id"),o.getString("client"),o.getString("phone"),o.getString("date"),o.getInt("days"),o.getLong("potential"),o.getBoolean("converted")));}
            JSONArray ea=root.optJSONArray("engineers");if(ea!=null)for(int i=0;i<ea.length();i++){JSONObject o=ea.getJSONObject(i);engineers.add(new EngineerItem(o.getString("id"),o.getString("name"),o.getInt("active"),o.getInt("planned"),o.getInt("overdue"),o.getInt("noReport")));}
            JSONArray ma=root.optJSONArray("managers");if(ma!=null)for(int i=0;i<ma.length();i++){JSONObject o=ma.getJSONObject(i);managers.add(new ManagerItem(o.getString("id"),o.getString("name"),o.getInt("leads"),o.getInt("surveys"),o.getInt("contracts"),o.getInt("installations")));}
            if(objects.isEmpty()||installers.isEmpty())seedDemoData();
        }catch(Exception e){seedDemoData();}
    }
    private void saveDemoState(){
        try{JSONObject r=new JSONObject();r.put("igor",igorBalance);r.put("konstantin",konstantinBalance);JSONArray oa=new JSONArray();for(ObjectItem o:objects){JSONObject x=new JSONObject();x.put("id",o.id);x.put("address",o.address);x.put("client",o.client);x.put("workType",o.workType);x.put("status",o.status);x.put("progress",o.progress);x.put("contract",o.contract);x.put("paid",o.paid);x.put("engineer",o.engineer);x.put("manager",o.manager);x.put("installers",o.installers);oa.put(x);}r.put("objects",oa);
            JSONArray ia=new JSONArray();for(InstallerItem i:installers){JSONObject x=new JSONObject();x.put("id",i.id);x.put("name",i.name);x.put("workDays",i.workDays);x.put("daysOff",i.daysOff);x.put("closed",i.closedObjects);x.put("accrued",i.accrued);x.put("paid",i.paid);x.put("tools",i.tools);x.put("uniform",i.uniformDate);x.put("status",i.status);ia.put(x);}r.put("installers",ia);
            JSONArray ta=new JSONArray();for(MoneyTx t:txs){JSONObject x=new JSONObject();x.put("type",t.type);x.put("title",t.title);x.put("sub",t.sub);x.put("amount",t.amount);ta.put(x);}r.put("txs",ta);
            JSONArray sa=new JSONArray();for(SurveyItem i:surveys){JSONObject x=new JSONObject();x.put("id",i.id);x.put("client",i.client);x.put("phone",i.phone);x.put("date",i.date);x.put("days",i.daysSinceContact);x.put("potential",i.potential);x.put("converted",i.converted);sa.put(x);}r.put("surveys",sa);
            JSONArray ea=new JSONArray();for(EngineerItem i:engineers){JSONObject x=new JSONObject();x.put("id",i.id);x.put("name",i.name);x.put("active",i.activeObjects);x.put("planned",i.planned);x.put("overdue",i.overdue);x.put("noReport",i.noReport);ea.put(x);}r.put("engineers",ea);
            JSONArray ma=new JSONArray();for(ManagerItem i:managers){JSONObject x=new JSONObject();x.put("id",i.id);x.put("name",i.name);x.put("leads",i.leads);x.put("surveys",i.surveys);x.put("contracts",i.contracts);x.put("installations",i.installations);ma.put(x);}r.put("managers",ma);
            prefs.edit().putString("state",r.toString()).putString("theme",themeMode).putString("leader",leaderName).putString("role",demoRole).putString("period",currentPeriod).putInt("calDay",selectedCalendarDay).apply();
        }catch(Exception ignored){}
    }

    static final class SimpleWatcher implements TextWatcher{private final Runnable r;SimpleWatcher(Runnable r){this.r=r;}public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){r.run();}public void afterTextChanged(Editable e){}}
    static final class SurveyItem{String id,client,phone,date;int daysSinceContact;long potential;boolean converted;SurveyItem(String id,String client,String phone,String date,int days,long potential,boolean converted){this.id=id;this.client=client;this.phone=phone;this.date=date;this.daysSinceContact=days;this.potential=potential;this.converted=converted;}}
    static final class EngineerItem{String id,name;int activeObjects,planned,overdue,noReport;EngineerItem(String id,String name,int a,int p,int o,int n){this.id=id;this.name=name;this.activeObjects=a;this.planned=p;this.overdue=o;this.noReport=n;}}
    static final class ManagerItem{String id,name;int leads,surveys,contracts,installations;ManagerItem(String id,String name,int l,int s,int c,int i){this.id=id;this.name=name;this.leads=l;this.surveys=s;this.contracts=c;this.installations=i;}}
    static final class TechTask{String objectId;Map<String,Long> installers=new LinkedHashMap<>();String day1a="Монтаж каркаса — 100 м²",day1b="Ветрозащитная мембрана — 100 м²",day2a="Задувной утеплитель — 120 м²",day2b="Контроль качества и фото ПОСЛЕ",note="Фото ДО / процесс / ПОСЛЕ обязательно";long pay1=300000,pay2=200000,pay3=180000;boolean saved=false;TechTask(String id){objectId=id;}}

    // ---------- models ----------
    static final class ObjectItem{
        String id,address,client,workType,status,engineer,manager,installers;int progress;long contract,paid;
        ObjectItem(String id,String address,String client,String workType,String status,int progress,long contract,long paid,String engineer,String manager,String installers){this.id=id;this.address=address;this.client=client;this.workType=workType;this.status=status;this.progress=progress;this.contract=contract;this.paid=paid;this.engineer=engineer;this.manager=manager;this.installers=installers;}
    }
    static final class InstallerItem{
        String id,name,uniformDate,status;int workDays,daysOff,closedObjects,tools;long accrued,paid;
        InstallerItem(String id,String name,int workDays,int daysOff,int closedObjects,long accrued,long paid,int tools,String uniformDate,String status){this.id=id;this.name=name;this.workDays=workDays;this.daysOff=daysOff;this.closedObjects=closedObjects;this.accrued=accrued;this.paid=paid;this.tools=tools;this.uniformDate=uniformDate;this.status=status;}
    }
    static final class MoneyTx{
        final String type,title,sub;final long amount;
        MoneyTx(String type,String title,String sub,long amount){this.type=type;this.title=title;this.sub=sub;this.amount=amount;}
    }
}

package ru.teplayakompaniya.tk4;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.util.Base64;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
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

    // Last verified server snapshot; legacy local demo records are never loaded.
    private final List<ObjectItem> objects = new ArrayList<>();
    private final List<InstallerItem> installers = new ArrayList<>();
    private final List<MoneyTx> txs = new ArrayList<>();
    private final List<SurveyItem> surveys = new ArrayList<>();
    private final List<EngineerItem> engineers = new ArrayList<>();
    private final List<ManagerItem> managers = new ArrayList<>();
    private final Map<String, TechTask> techTasks = new HashMap<>();

    private long igorBalance = 0;
    private long konstantinBalance = 0;
    private String currentPeriod = "Месяц";
    private String objectFilter = "Все";
    private String financeFilter = "Все";
    private String analyticsFilter = "Все объекты";
    private int selectedCalendarDay = 12;
    private String themeMode = "Светлая";
    private String leaderName = "Профиль";
    private String demoRole = "Руководитель";
    private SharedPreferences prefs;
    private ApiClient api;
    private boolean liveSyncOk = false;
    private boolean syncInProgress = false;
    private String lastSyncText = "Ещё не выполнялась";
    private final Map<String,Long> liveKpis = new HashMap<>();
    private final List<AttentionItem> liveAttention = new ArrayList<>();
    private final List<PaymentItem> livePaymentPlan = new ArrayList<>();
    private final List<MediaItem> liveMedia = new ArrayList<>();
    private final List<CalendarItem> liveCalendar = new ArrayList<>();
    private String pendingMediaObjectId = null;
    private static final int REQ_PICK_MEDIA = 4107;
    private static final int REQ_AVATAR = 4108;
    private String apiRole = "";
    private boolean financeGranted = false;
    private boolean scopedData = false;
    private JSONObject snapshot = new JSONObject();
    private String snapshotPeriod = "";
    private String attentionFilter = "Все";
    private boolean attentionExpanded = false;
    private String calendarMode = "Производство";
    private String calendarView = "Неделя";
    private LocalDate calendarDate = LocalDate.now();
    private ObjectAnimator loadingPulse;
    private float refreshStartX, refreshStartY;
    private boolean refreshEligible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("tk4_connected", MODE_PRIVATE);
        loadDemoState();
        api = new ApiClient(this, prefs);
        apiRole=api.getRole();
        restoreSnapshot();
        configureSystemBars();

        root = new FrameLayout(this);
        root.setBackgroundColor(screenBg());
        setContentView(root);
        showMain(false);

        if (ApiClient.isConfigured()) {
            if (api.hasToken()) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> syncNow(false), 350);
            } else {
                new Handler(Looper.getMainLooper()).postDelayed(this::showPairingDialog, 500);
            }
        }
    }

    @Override protected void onDestroy(){if(loadingPulse!=null)loadingPulse.cancel();super.onDestroy();}

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
        liveKpis.clear(); liveAttention.clear(); livePaymentPlan.clear(); liveMedia.clear(); liveCalendar.clear();
        igorBalance=0; konstantinBalance=0;
    }

    // ---------- navigation ----------

    private void navigate(String target) {
        if(!canNavigate(target)){toast("Раздел недоступен для вашей роли");return;}
        if (!screen.equals(target)) history.push(screen);
        screen = target;
        render();
    }

    private void render() {
        configureSystemBars();
        if(!canNavigate(screen))screen="main";
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
        if(loadingPulse!=null){loadingPulse.cancel();loadingPulse=null;}
        root.removeAllViews();
        root.setBackgroundColor(screenBg());

        ScrollView scroll = new ScrollView(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent e) {
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                    refreshStartX=e.getX();refreshStartY=e.getY();refreshEligible=getScrollY()==0;
                }else if(e.getActionMasked()==MotionEvent.ACTION_UP && refreshEligible && !syncInProgress
                        && e.getY()-refreshStartY>dp(110) && Math.abs(e.getX()-refreshStartX)<dp(70)){
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); syncNow(false);
                }
                return super.dispatchTouchEvent(e);
            }
        };
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(screenBg());

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(showBottomNav ? 92 : 24));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(scroll, sp);
        if (showBottomNav) addBottomNav();
        if(syncInProgress){
            LinearLayout loading=h();loading.setPadding(dp(12),dp(10),dp(12),dp(10));
            ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.company_logo);
            loading.addView(mark,new LinearLayout.LayoutParams(dp(28),dp(28)));
            TextView message=tv("Обновляем данные…",13,GREEN,Typeface.BOLD);message.setPadding(dp(10),0,0,0);loading.addView(message);
            content.addView(loading);
            if(prefs.getBoolean("animations",true)){
                loadingPulse=ObjectAnimator.ofFloat(mark,"alpha",0.35f,1f);loadingPulse.setDuration(650);
                loadingPulse.setRepeatCount(ValueAnimator.INFINITE);loadingPulse.setRepeatMode(ValueAnimator.REVERSE);loadingPulse.start();
            }
        }else if(liveSyncOk){content.addView(tv(lastSyncText,11,MUTED,Typeface.NORMAL));}
        content.post(()->applyInteractionFeedback(root));
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
                {"▥","Аналитика","analytics"},
                {"⚙","Настройки","settings"}
        };
        for (String[] it : items) {
            if(!canNavigate(it[2]) && !"quickAdd".equals(it[2])) continue;
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setPadding(dp(4), dp(2), dp(4), dp(2));
            boolean active = it[2].equals(screen) || ("main".equals(it[2]) && "main".equals(screen));
            TextView icon = tv(it[0], it[2].equals("quickAdd") ? 28 : 22, active ? GREEN : MUTED, Typeface.BOLD);
            TextView label = tv(it[1], 10, active ? GREEN : MUTED, Typeface.NORMAL);
            icon.setGravity(Gravity.CENTER);label.setGravity(Gravity.CENTER);
            box.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(32)));box.addView(label);
            if(active)box.setBackground(round(light(GREEN),18));
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

        TextView menu = pillText("⋮",22,INK,softBg());
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

        TextView bell = pillText("⋮",24,INK,softBg()); bell.setGravity(Gravity.CENTER); bell.setOnClickListener(v -> showScreenMenu());
        brandRow.addView(bell,new LinearLayout.LayoutParams(dp(44),dp(44))); content.addView(brandRow);

        LinearLayout hello = h();
        View avatar=avatarView(); avatar.setOnClickListener(v->editProfileDialog());
        hello.addView(avatar,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout htxt = v(); htxt.setPadding(dp(10),0,0,0);
        htxt.addView(tv("Добрый день,",12,MUTED,Typeface.NORMAL));
        htxt.addView(tv(leaderName,20,INK,Typeface.BOLD));
        htxt.addView(tv(demoRole,11,MUTED,Typeface.NORMAL));
        hello.addView(htxt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        LinearLayout date = v(); date.setGravity(Gravity.CENTER_VERTICAL);
        date.addView(tv("Сегодня",11,MUTED,Typeface.NORMAL));
        date.addView(tv(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM",new Locale("ru","RU"))),12,INK,Typeface.BOLD)); hello.addView(date);
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
            t.setOnClickListener(v->selectPeriod(x));
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

    private LinearLayout kpiCard(String icon,String label,String value,String delta,int tint,String target){
        LinearLayout c=v();c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(round(light(tint),22));c.setElevation(dp(1));
        c.setMinimumHeight(dp(166));
        TextView symbol=pillText(icon,20,tint,Color.TRANSPARENT);symbol.setGravity(Gravity.CENTER);
        c.addView(symbol,new LinearLayout.LayoutParams(dp(32),dp(32)));
        TextView title=tv(label,13,INK,Typeface.BOLD);title.setMaxLines(2);c.addView(title,new LinearLayout.LayoutParams(-1,dp(38)));
        TextView amount=tv(value,23,INK,Typeface.BOLD);amount.setMaxLines(1);amount.setAutoSizeTextTypeUniformWithConfiguration(12,23,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        c.addView(amount,new LinearLayout.LayoutParams(-1,dp(34)));
        TextView note=tv(delta==null?"":delta,11,MUTED,Typeface.NORMAL);note.setMaxLines(2);c.addView(note,new LinearLayout.LayoutParams(-1,dp(32)));
        if(target!=null)c.setOnClickListener(v->navigate(target));return c;
    }

    private LinearLayout miniCard(String icon,String label,String value,int tint,String target){
        LinearLayout c=v();c.setPadding(dp(12),dp(12),dp(12),dp(12));c.setBackground(round(cardBg(),22));c.setElevation(dp(1));c.setMinimumHeight(dp(110));
        TextView title=tv(label,12,MUTED,Typeface.NORMAL);title.setMaxLines(2);c.addView(title,new LinearLayout.LayoutParams(-1,dp(34)));
        TextView amount=tv(value,17,INK,Typeface.BOLD);amount.setMaxLines(2);c.addView(amount,new LinearLayout.LayoutParams(-1,dp(48)));
        if(target!=null)c.setOnClickListener(v->navigate(target));return c;
    }

    private LinearLayout quickCard(String icon,String label,int tint,String target){
        LinearLayout c=v();c.setGravity(Gravity.CENTER);c.setPadding(dp(8),dp(12),dp(8),dp(12));c.setBackground(round(cardBg(),22));c.setElevation(dp(1));
        TextView symbol=tv(icon,24,tint,Typeface.NORMAL);symbol.setGravity(Gravity.CENTER);c.addView(symbol,new LinearLayout.LayoutParams(dp(32),dp(32)));
        TextView text=tv(label,12,INK,Typeface.BOLD);text.setGravity(Gravity.CENTER);text.setMaxLines(2);c.addView(text,new LinearLayout.LayoutParams(-1,dp(36)));
        if(target!=null)c.setOnClickListener(v->navigate(target));return c;
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

    private void showMain(boolean fromNav){
        screen="main";beginScreen(true);approvedHeader();periodSelector();
        if(!apiRole.isEmpty() && Stage1Rules.needsScopedData(apiRole) && !scopedData){
            content.addView(emptyState("Доступ ожидает настройки","Сервер пока не передаёт данные, ограниченные вашей ролью. Общие данные компании скрыты."));return;
        }
        if(canFinance()){
            LinearLayout a=h();a.addView(kpiCard("₽","Оборот",metric("turnover",true),"Фактические поступления",GREEN,"kpi:turnover"),weight());
            a.addView(kpiCard("↓","Расходы",metric("expenses",true),"Фактические расходы за период",RED,"kpi:expenses"),weightMarginLeft());content.addView(a);spacer(10);
        }
        LinearLayout b=h();
        if(canFinance())b.addView(kpiCard("▥","Прибыль",metric("profit",true),"Операционная прибыль",BLUE,"kpi:profit"),weight());
        b.addView(kpiCard("⌂","Объекты",hasData("objects")?String.valueOf(objects.size()):"Не заполнено","Все актуальные объекты",GREEN,"objects"),canFinance()?weightMarginLeft():weight());content.addView(b);spacer(10);
        if(canFinance())content.addView(kpiCard("◷","Планируется поступление",plannedMetric(),"Будущие неоплаченные этапы ТЗ",BLUE,"kpi:plan_income"));
        sectionTitle("Ключевые показатели",null,null);
        String[][] metrics={{"Лиды","leads","kpi:leads"},{"Замеры","surveys","surveys"},{"Договоры","contracts","kpi:contracts"},{"Средний чек","averageCheck","kpi:avg"},{"Дебиторка","debt","kpi:debt"}};
        LinearLayout row=null;int n=0;
        for(String[] item:metrics){if(!canNavigate(item[2]))continue;if(n%2==0){row=h();content.addView(row);spacer(8);}row.addView(miniCard("",item[0],metric(item[1],item[1].equals("averageCheck")||item[1].equals("debt")),GREEN,item[2]),n++%2==0?weight():weightMarginLeft());}
        sectionTitle("Быстрый доступ",null,null);
        String[][] links={{"⌂","Объекты","objects"},{"♟","Монтажники","installers"},{"▤","Инженеры","engineers"},{"●","Менеджеры","managers"},{"₽","Финансы","finance"},{"▥","Аналитика","analytics"},{"▦","Календарь","calendar"},{"⚙","Настройки","settings"}};
        n=0;for(String[] item:links){if(!canNavigate(item[2]))continue;if(n%2==0){row=h();content.addView(row);spacer(8);}row.addView(quickCard(item[0],item[1],GREEN,item[2]),n++%2==0?weight():weightMarginLeft());}
        sectionTitle("Сегодня требует внимания","Все уведомления",v->navigate("kpi:notifications"));
        View summary=attention("!",hasData("attention")?liveAttention.size()+" событий":"Недостаточно данных",attentionExpanded?"Свернуть":"Показать кратко",ORANGE,null);
        summary.setOnClickListener(v->{attentionExpanded=!attentionExpanded;render();});content.addView(summary);
        if(attentionExpanded)for(int i=0;i<Math.min(3,liveAttention.size());i++){AttentionItem a=liveAttention.get(i);content.addView(attention("!",a.title,a.subtitle,"red".equals(a.severity)?RED:ORANGE,a.target));}
    }

    // ---------- objects ----------

    private void showObjects(){
        beginScreen(true);appBar("Объекты","Актуальные объекты");
        if(!hasData("objects")){content.addView(emptyState("Не заполнено","Нет ответа API для выбранного периода"));return;}
        String[] groups={"Все","В работе","Запланированы и подтверждены","Ожидают даты","Завершены"};
        Button filter=primaryOutline(objectFilter+" ▾");filter.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Состояние объекта").setItems(groups,(d,w)->{objectFilter=groups[w];showObjects();}).show());content.addView(filter);
        EditText search=edit("Телефон: последние 4 цифры / клиент / адрес");content.addView(search,lpMatch(dp(56),8));
        Button plus=primaryOutline("+ Добавить");plus.setOnClickListener(v->new AlertDialog.Builder(this).setItems(new String[]{"Создать объект","Создать замер"},(d,w)->{if(w==0)navigate("create");else newSurveyDialog();}).show());content.addView(plus);
        LinearLayout list=v();content.addView(list);
        Runnable rebuild=()->{list.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);int count=0;
            for(ObjectItem o:objects){if(!objectFilter.equals("Все")&&!Stage1Rules.objectGroup(o.status).equals(objectFilter))continue;
                if(!q.isEmpty()&&!o.phone.replaceAll("[^0-9]","").endsWith(q)&&!(o.address+" "+o.client+" "+o.id).toLowerCase(Locale.ROOT).contains(q))continue;
                list.addView(objectCard(o));count++;}
            if(count==0)list.addView(emptyState("Объекты не найдены","Измените фильтр или поиск"));applyInteractionFeedback(list);
        };search.addTextChangedListener(new SimpleWatcher(rebuild));rebuild.run();
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
        mid.addView(tv(o.planStart.isEmpty()?"Дата не заполнена":o.planStart+" — "+o.planEnd,11,MUTED,Typeface.NORMAL));
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
        Button call=smallButton("☎ Клиент",GREEN); call.setOnClickListener(v->dial(o.phone));
        Button map=smallButton("⌖ Адрес",BLUE); map.setOnClickListener(v->openMap(o.address));
        Button status=smallButton("Статус",ORANGE); status.setOnClickListener(v->changeObjectStatus(o));
        actions.addView(call,weight());actions.addView(map,weightMarginLeft());actions.addView(status,weightMarginLeft()); content.addView(actions,lpMatch(dp(46),8));

        sectionTitle("Клиент и объект",null,null);
        content.addView(clickableInfoRow("Клиент",o.client,v->dial(o.phone)));
        content.addView(clickableInfoRow("Телефон",o.phone==null||o.phone.isEmpty()?"Не указан":o.phone,v->dial(o.phone)));
        content.addView(clickableInfoRow("Адрес",o.address,v->openMap(o.address)));
        content.addView(infoRow("Вид работ",o.workType)); content.addView(clickableInfoRow("Инженер",o.engineer,v->navigate("engineers")));
        content.addView(clickableInfoRow("Менеджер",o.manager,v->navigate("managers"))); content.addView(clickableInfoRow("Монтажники",o.installers,v->navigate("installers")));

        if(o.planStart.isEmpty()&&!Stage1Rules.isClosed(o.status)){
            Button schedule=primaryOutline("Запланировать дату");schedule.setOnClickListener(v->scheduleObject(o));content.addView(schedule);
        }
        sectionTitle("Финансы объекта",null,null); LinearLayout f=h();
        f.addView(miniCard("₽","Договор",money(o.contract),GREEN,"kpi:contract_object"),weight());
        f.addView(miniCard("↓","Получено",money(o.paid),BLUE,"payments:"+o.id),weightMarginLeft()); content.addView(f); spacer(7);
        content.addView(miniCard("◷","Осталось получить",money(Math.max(0,o.contract-o.paid)),ORANGE,"payments:"+o.id));

        sectionTitle("Монтажники",null,null);
        Button hired=primaryOutline("Добавить → Наёмник");hired.setOnClickListener(v->workforceUi().hiredDay(null,o.id));content.addView(hired);
        Button move=primaryOutline("Переместить на другой объект");move.setOnClickListener(v->{String[] names=new String[installers.size()];for(int index=0;index<names.length;index++)names[index]=installers.get(index).name+" · "+installers.get(index).id;new AlertDialog.Builder(this).setTitle("Монтажник").setItems(names,(dialog,index)->workforceUi().moveInstaller(installers.get(index).id)).show();});content.addView(move);
        sectionTitle("Управление объектом",null,null);
        content.addView(attention("▤",techTasks.containsKey(o.id)&&techTasks.get(o.id).saved?"ТЗ создано":"Создать техническое задание","Монтажники · зарплата · план по дням · график оплат",ORANGE,"tech:"+o.id));
        content.addView(attention("₽","График платежей","Плановые, полученные и просроченные этапы",GREEN,"payments:"+o.id));
        content.addView(attention("▦","Фотоотчёты","ДО · процесс · ПОСЛЕ · комментарии по дням",BLUE,"photos:"+o.id));
        if(!Stage1Rules.isClosed(o.status))content.addView(attention("↓","Добавить расход по объекту","Материалы, топливо, инструмент, прочее",RED,"kpi:expense_object"));
    }

    private void showCreateObject() {
        beginScreen(true); appBar("Создать объект","Новый объект в системе");
        LinearLayout sync=v();sync.setPadding(dp(12),dp(10),dp(12),dp(10));sync.setBackground(round(light(GREEN),16));
        sync.addView(tv(liveSyncOk?"● GOOGLE SHEETS СИНХРОНИЗИРОВАНА":"● ОЖИДАНИЕ СИНХРОНИЗАЦИИ",13,GREEN_DARK,Typeface.BOLD));
        sync.addView(tv(liveSyncOk?"Объект будет сразу записан в рабочую таблицу 4.2.":"Проверьте подключение в Настройки → Синхронизация.",10,MUTED,Typeface.NORMAL));content.addView(sync);

        final EditText client=field("Клиент *",""); final EditText phone=field("Телефон","");
        final EditText address=field("Адрес объекта *","");
        final EditText type=choiceField("Вид работ *","Комплексное утепление",new String[]{"Комплексное утепление","Утепление пола","Утепление стен","Мансарда","Фасад","Фасад + утепление"});
        final EditText sum=field("Сумма договора",""); final EditText survey=field("Дата замера",""); final EditText plan=field("Плановая дата монтажа","");
        final EditText status=choiceField("Статус объекта","Запланирован",new String[]{"Запланирован","Подтверждён"});
        final EditText engineer=choiceField("Инженер","",engineerNames()); final EditText manager=choiceField("Менеджер","",managerNames());

        sectionTitle("После сохранения",null,null);content.addView(attention("▤","Техническое задание","Можно сразу назначить монтажников, суммы и план по дням.",ORANGE,null));

        Button save=primary("Сохранить объект");
        save.setOnClickListener(v->{
            if(client.getText().toString().trim().isEmpty()||address.getText().toString().trim().isEmpty()){toast("Заполните клиента и адрес");return;}
            if(api!=null && api.hasToken()){
                try{
                    JSONObject b=new JSONObject();
                    if(status.getText().toString().equals("Подтверждён")&&plan.getText().toString().trim().isEmpty()){toast("Выберите подтверждённую дату");return;}
                    b.put("client",client.getText().toString().trim());
                    b.put("phone",phone.getText().toString().trim());
                    b.put("address",address.getText().toString().trim());
                    b.put("workType",type.getText().toString().trim());
                    b.put("contract",parseLong(sum.getText().toString()));
                    b.put("planStart",plan.getText().toString().trim());
                    b.put("status",status.getText().toString().trim());
                    b.put("responsible",manager.getText().toString().trim());
                    b.put("comment","Инженер: "+engineer.getText().toString().trim()+"; Замер: "+survey.getText().toString().trim());
                    setBusy(save,true);
                    api.mutate("createObject",b,new ApiClient.Callback(){
                        public void onSuccess(JSONObject json){
                            setBusy(save,false);
                            String id=json.optString("id");
                            syncNow(false);
                            new AlertDialog.Builder(MainActivity.this).setTitle("Объект создан в Google Sheets")
                                    .setMessage("Object_ID: "+id+"\n\nОткрыть техническое задание?")
                                    .setPositiveButton("Создать ТЗ",(d,w)->navigate("tech:"+id))
                                    .setNegativeButton("К объектам",(d,w)->navigate("objects")).show();
                        }
                        public void onError(String error){setBusy(save,false);showApiError(error);}
                    });
                }catch(Exception ex){showApiError(ex.toString());}
            }else{
                toast("Сначала подключите приложение к Google Sheets");
                showPairingDialog();
            }
        }); content.addView(save);
    }

    // ---------- tech task ----------

    private void showTechTask(String objectId) {
        ObjectItem obj=findObject(objectId);
        if(obj==null){navigate("objects");return;}
        if(!Stage1Rules.canCreateTask(obj.status,obj.planStart)){unavailable("ТЗ недоступно","Сначала подтвердите объект и дату начала монтажа");return;}
        if(techTasks.containsKey(objectId)||hasPaymentRows(objectId)){showSavedTechTask(objectId);return;}
        if(!hasData("techTasks")||!hasData("paymentPlan")){unavailable("ТЗ недоступно","Сначала синхронизируйте задания и графики оплат");return;}
        beginScreen(true); appBar("Техническое задание","Инженер · "+obj.address);
        TechTask existing=techTasks.get(objectId); if(existing==null) existing=new TechTask(objectId); final TechTask task=existing;
        final Map<String,CheckBox> checks=new LinkedHashMap<>(); final Map<String,EditText> wages=new LinkedHashMap<>();

        sectionTitle("Общие данные ТЗ","Object_ID: "+objectId,null);
        Button hired=primaryOutline("Монтажники → Добавить → Наёмник");hired.setOnClickListener(v->workforceUi().hiredDay(null,objectId));content.addView(hired);
        final EditText planStart=taskField("План начала (дд.мм.гггг)",obj.planStart);
        final EditText planEnd=taskField("План окончания (дд.мм.гггг)",obj.planEnd);
        final EditText windowFrom=new EditText(this);
        final EditText windowTo=new EditText(this);
        CheckBox materialsReady=new CheckBox(this);materialsReady.setText("Материалы / ресурсы подготовлены");materialsReady.setTextColor(resolveText(INK));content.addView(materialsReady);

        sectionTitle("Монтажники на объекте","Конкретные сотрудники и согласованная сумма",null);
        for(InstallerItem i:installers){
            LinearLayout r=h();r.setPadding(dp(9),dp(8),dp(9),dp(8));r.setBackground(round(cardBg(),14));
            CheckBox cb=new CheckBox(this);cb.setChecked(task.installers.containsKey(i.name));checks.put(i.name,cb);r.addView(cb,new LinearLayout.LayoutParams(dp(44),dp(44)));
            LinearLayout name=v();name.addView(tv(i.name,12,INK,Typeface.BOLD));name.addView(tv(i.status,9,MUTED,Typeface.NORMAL));r.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            EditText amount=edit("Сумма");amount.setInputType(InputType.TYPE_CLASS_NUMBER);Long saved=task.installers.get(i.name);amount.setText(saved==null?"":String.valueOf(saved));wages.put(i.name,amount);r.addView(amount,new LinearLayout.LayoutParams(dp(100),dp(44)));
            LinearLayout.LayoutParams rp=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);rp.setMargins(0,0,0,dp(6));content.addView(r,rp);
        }

        sectionTitle("План объекта по дням","Факт затем заполняется монтажником",null);
        final EditText d1a=taskField("День 1 — задача 1",task.saved?task.day1a:"");
        final EditText d1b=taskField("День 1 — задача 2",task.saved?task.day1b:"");
        final EditText d2a=taskField("День 2 — задача 1",task.saved?task.day2a:"");
        final EditText d2b=taskField("День 2 — задача 2",task.saved?task.day2b:"");
        final EditText note=taskField("Комментарий монтажникам",task.saved?task.note:"");

        sectionTitle("График оплат клиента","Дебиторка = только просроченные этапы",null);
        final EditText pd1=taskField("Этап 1 — дата","");
        final EditText p1=taskField("Этап 1 — сумма",task.saved&&task.pay1>0?String.valueOf(task.pay1):"");
        final EditText pd2=taskField("Этап 2 — дата","");
        final EditText p2=taskField("Этап 2 — сумма",task.saved&&task.pay2>0?String.valueOf(task.pay2):"");
        final EditText pd3=taskField("Этап 3 — дата","");
        final EditText p3=taskField("Этап 3 — сумма",task.saved&&task.pay3>0?String.valueOf(task.pay3):"");

        Button save=primary("Сохранить ТЗ в Google Sheets");
        save.setOnClickListener(v->{
            if(api==null||!api.hasToken()){showPairingDialog();return;}
            try{
                JSONObject b=new JSONObject();
                b.put("objectId",objectId);b.put("status","Готово");b.put("engineer",api.getUserName());
                b.put("workType",obj.workType);b.put("planStart",planStart.getText().toString().trim());b.put("planEnd",planEnd.getText().toString().trim());
                b.put("windowFrom",windowFrom.getText().toString().trim());b.put("windowTo",windowTo.getText().toString().trim());
                b.put("materialsReady",materialsReady.isChecked());b.put("comment",note.getText().toString().trim());

                JSONArray asn=new JSONArray();task.installers.clear();
                for(InstallerItem i:installers){
                    CheckBox cb=checks.get(i.name); if(cb!=null&&cb.isChecked()){
                        long wage=parseLong(wages.get(i.name).getText().toString());
                        if(wage<=0){toast("Укажите согласованную сумму для "+i.name);return;}
                        JSONObject a=new JSONObject();a.put("installerId",i.id);a.put("name",i.name);a.put("agreedAmount",wage);a.put("status","Назначен");asn.put(a);
                        task.installers.put(i.name,wage);
                    }
                }
                if(asn.length()==0){toast("Назначьте хотя бы одного монтажника");return;}b.put("assignments",asn);

                JSONArray days=new JSONArray();
                String[] tasks={d1a.getText().toString().trim(),d1b.getText().toString().trim(),d2a.getText().toString().trim(),d2b.getText().toString().trim()};
                int[] dns={1,1,2,2};
                for(int i=0;i<tasks.length;i++)if(!tasks[i].isEmpty()){JSONObject x=new JSONObject();x.put("dayNo",dns[i]);x.put("task",tasks[i]);x.put("status","План");days.put(x);}
                if(days.length()==0){toast("Заполните план хотя бы на один день");return;}b.put("days",days);

                JSONArray pays=new JSONArray();
                String[] dates={pd1.getText().toString().trim(),pd2.getText().toString().trim(),pd3.getText().toString().trim()};
                EditText[] amounts={p1,p2,p3};
                for(int i=0;i<3;i++){
                    long a=parseLong(amounts[i].getText().toString());
                    if(a>0&&!dates[i].isEmpty()){JSONObject x=new JSONObject();x.put("stageNo",i+1);x.put("stageName","Этап "+(i+1));x.put("plannedDate",dates[i]);x.put("plannedAmount",a);pays.put(x);}
                }
                if(pays.length()==0){toast("Добавьте хотя бы один этап оплаты");return;}b.put("payments",pays);

                setBusy(save,true);
                api.mutate("saveTechTask",b,new ApiClient.Callback(){
                    public void onSuccess(JSONObject json){
                        setBusy(save,false);task.saved=true;task.day1a=tasks[0];task.day1b=tasks[1];task.day2a=tasks[2];task.day2b=tasks[3];task.note=note.getText().toString();
                        task.pay1=parseLong(p1.getText().toString());task.pay2=parseLong(p2.getText().toString());task.pay3=parseLong(p3.getText().toString());techTasks.put(objectId,task);
                        toast("ТЗ записано и связано с объектом");syncNow(false);
                    }
                    public void onError(String error){setBusy(save,false);showApiError(error);}
                });
            }catch(Exception ex){showApiError(ex.toString());}
        });content.addView(save);spacer(7);

        Button share=primaryOutline("Поделиться ТЗ");share.setOnClickListener(v->shareTechTask(objectId));content.addView(share);
        Button pdf=primaryOutline("PDF / Сохранить");pdf.setOnClickListener(v->Stage1Pdf.export(this,"ТЗ-"+objectId,techTaskText(objectId)));content.addView(pdf);
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
        Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");share.putExtra(Intent.EXTRA_SUBJECT,"ТЗ — "+objectId);share.putExtra(Intent.EXTRA_TEXT,techTaskText(objectId));startActivity(Intent.createChooser(share,"Поделиться ТЗ"));
    }

    // ---------- installers ----------

    private void showInstallers(){
        beginScreen(true);appBar("Монтажники","Справочник из Google Sheets");
        if(!hasData("employees")){content.addView(emptyState("Не заполнено","Нет списка сотрудников от API"));return;}
        for(InstallerItem installer:installers)content.addView(installerCard(installer));
        if(installers.isEmpty())content.addView(emptyState("Монтажники не заполнены","Сервер вернул пустой справочник"));
    }

    private View installerCard(InstallerItem installer){return clickableInfoRow(installer.name,(installer.hired?"Наёмник":"Монтажник")+" ›",v->navigate("installer:"+installer.id));}

    private void showInstallerDetail(String id){
        InstallerItem installer=findInstaller(id);if(installer==null){navigate("installers");return;}
        beginScreen(true);appBar(installer.name,(installer.hired?"Наёмник":"Монтажник")+" · "+id);
        if(hasData("payroll")){content.addView(infoRow("Начислено по доступным записям",money(installer.accrued)));content.addView(infoRow("Выплачено по доступным записям",money(installer.paid)));}
        if(!installer.hired)content.addView(emptyState("Недостаточно данных","API не отдаёт подтверждённую загрузку, рабочие дни, инструмент и СИЗ."));
        sectionTitle("Назначенные объекты",null,null);JSONArray assignments=snapshot.optJSONArray("assignments");Set<String> seen=new HashSet<>();
        if(assignments!=null)for(int n=0;n<assignments.length();n++){JSONObject assignment=assignments.optJSONObject(n);if(assignment==null||!id.equals(assignment.optString("Installer_ID")))continue;ObjectItem object=findObject(assignment.optString("Object_ID"));if(object!=null&&seen.add(object.id))content.addView(clickableInfoRow(object.address,object.status,v->navigate("object:"+object.id)));}
        sectionTitle("История назначений",null,null);
        if(assignments!=null)for(int n=0;n<assignments.length();n++){JSONObject assignment=assignments.optJSONObject(n);if(assignment==null||!id.equals(assignment.optString("Installer_ID")))continue;
            content.addView(infoRow("Object_ID / Assignment_ID",assignment.optString("Object_ID")+" / "+assignment.optString("Assignment_ID")));
            content.addView(infoRow("Период",displayField(assignment,"startDate")+" — "+displayField(assignment,"endDate")));
            for(String[] row:new String[][]{{"Фактические дни","actualDays"},{"Тип оплаты","paymentType"},{"Начислено","accrued"},{"Выплачено","paid"},{"Осталось выплатить","remaining"}})content.addView(infoRow(row[0],displayField(assignment,row[1])));
        }
        Button move=primaryOutline("Переместить на другой объект");move.setOnClickListener(v->workforceUi().moveInstaller(id));content.addView(move);
        if(installer.hired){Button add=primaryOutline("Добавить рабочий день");add.setOnClickListener(v->workforceUi().hiredDay(id,null));content.addView(add);
            if(Stage1Rules.isAdmin(apiRole)){Button promote=primaryOutline("Перевести в монтажники");promote.setOnClickListener(v->workforceUi().promote(id));content.addView(promote);}}
    }

    // ---------- finance ----------

    private void showFinance(){
        beginScreen(true);appBar("Финансы","Приход / Расход / Передать");periodSelector();
        LinearLayout totals=h();totals.addView(kpiCard("₽","Оборот",metric("turnover",true),"Из API",GREEN,"kpi:turnover"),weight());totals.addView(kpiCard("↓","Расходы",metric("expenses",true),"Без внутренних переводов",RED,"kpi:expenses"),weightMarginLeft());content.addView(totals);
        content.addView(clickableInfoRow("Подотчёт",metric("totalAccountable",true),v->navigate("accountable:all")));
        LinearLayout actions=h();String[] titles={"Приход","Расход","Передать"};for(int i=0;i<3;i++){final int action=i;Button b=smallButton(titles[i],GREEN);b.setOnClickListener(v->{if(action==2)transferDialog();else moneyDialog(action==0?"INCOME":"EXPENSE");});actions.addView(b,i==0?weight():weightMarginLeft());}content.addView(actions);
        String[] filters={"Все","Приходы","Расходы","Переводы","По объектам","Монтажники","Маркетинг"};Button filter=primaryOutline(financeFilter+" ▾");filter.setOnClickListener(v->new AlertDialog.Builder(this).setItems(filters,(d,w)->{financeFilter=filters[w];showFinance();}).show());content.addView(filter);
        content.addView(tv("API передаёт ограниченный список последних операций. Полнота истории за период пока не подтверждена.",11,MUTED,Typeface.NORMAL));
        int count=0;for(MoneyTx t:txs){if(!financeMatches(t))continue;View row=attention(t.type.equals("TRANSFER")?"↔":"₽",t.date+" · "+t.title,t.sub+" · "+money(t.amount),t.type.equals("EXPENSE")?RED:GREEN,null);row.setOnClickListener(v->showOperation(t));content.addView(row);count++;}
        if(count==0)content.addView(emptyState(hasData("income")?"Нет операций в выборке":"Недостаточно данных","Обновите данные или измените фильтр"));
    }

    private void moneyDialog(String type){
        if(!canFinance()){toast("Нет доступа к финансам");return;}
        LinearLayout form=v();form.setPadding(dp(18),0,dp(18),0);
        EditText amt=edit("Сумма");amt.setInputType(InputType.TYPE_CLASS_NUMBER);form.addView(labelWrap("Сумма",amt));

        Spinner category=new Spinner(this);
        String[] cats=type.equals("INCOME")
                ?new String[]{"Аванс","Промежуточный платёж","Окончательный расчёт","Прочий приход","Возврат клиенту"}
                :new String[]{"Материалы","Зарплата бригады","Топливо","Расходники","Инструмент","Доставка / логистика","Проживание","Реклама","Аренда","Офис и склад","Связь и интернет","Личные","Прочее"};
        category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,cats));
        form.addView(labelWrap(type.equals("INCOME")?"Вид платежа":"Статья расхода",category));

        Spinner owner=new Spinner(this);
        owner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Игорь","Константин"}));
        form.addView(labelWrap(type.equals("INCOME")?"Получил":"Подотчётное лицо",owner));

        Spinner target=new Spinner(this);
        List<String> targets=new ArrayList<>();targets.add("Без привязки");
        final List<ObjectItem> selectableObjects=new ArrayList<>(objects);
        final List<InstallerItem> selectableInstallers=new ArrayList<>(installers);
        for(ObjectItem o:selectableObjects)targets.add(o.id+" · "+o.address);
        if(type.equals("EXPENSE"))for(InstallerItem i:selectableInstallers)targets.add("Монтажник: "+i.id+" · "+i.name);
        target.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,targets));
        form.addView(labelWrap("Объект / монтажник",target));

        EditText comment=edit("Комментарий");
        form.addView(labelWrap("Комментарий",comment));

        String title=type.equals("INCOME")?"Добавить приход":"Добавить расход";
        new AlertDialog.Builder(this).setTitle(title).setView(form).setPositiveButton("Сохранить",(d,w)->{
            long v=parseLong(amt.getText().toString());if(v<=0){toast("Сумма не указана");return;}
            if(api==null||!api.hasToken()){showPairingDialog();return;}
            String who=owner.getSelectedItem().toString();
            String targetValue=target.getSelectedItem().toString();
            String cat=category.getSelectedItem().toString();
            try{
                JSONObject b=new JSONObject();
                b.put("amount",v);b.put("comment",comment.getText().toString().trim());
                if(type.equals("INCOME")){
                    int selected=target.getSelectedItemPosition();
                    ObjectItem o=selected>0&&selected<=selectableObjects.size()?selectableObjects.get(selected-1):null;
                    if(o==null){toast("Для прихода выберите объект");return;}
                    b.put("objectId",o.id);b.put("paymentKind",cat);
                    b.put("operation","Возврат клиенту".equals(cat)?"Возврат клиенту":"Приход");
                    b.put("recipient",who);b.put("method","Наличные");
                    api.mutate("addIncome",b,new ApiClient.Callback(){
                        public void onSuccess(JSONObject json){toast("Приход записан в таблицу");syncNow(false);}
                        public void onError(String error){showApiError(error);}
                    });
                }else{
                    String expenseType="Прочий расход";String objectId="";String installerId="";
                    int selected=target.getSelectedItemPosition();
                    ObjectItem o=selected>0&&selected<=selectableObjects.size()?selectableObjects.get(selected-1):null;
                    if(o!=null){expenseType="По объекту";objectId=o.id;}
                    if(targetValue.startsWith("Монтажник: ")){
                        int installerIndex=selected-selectableObjects.size()-1;
                        InstallerItem ins=installerIndex>=0&&installerIndex<selectableInstallers.size()?selectableInstallers.get(installerIndex):null;
                        if(ins!=null)installerId=ins.id;
                    }
                    b.put("type",expenseType);b.put("objectId",objectId);b.put("installerId",installerId);
                    b.put("article",cat);b.put("description",comment.getText().toString().trim().isEmpty()?cat:comment.getText().toString().trim());
                    b.put("quantity",1);b.put("unit","руб.");b.put("unitPrice",v);
                    b.put("method","Наличные");b.put("paymentStatus","Оплачено");b.put("accountable",who);
                    api.mutate("addExpense",b,new ApiClient.Callback(){
                        public void onSuccess(JSONObject json){toast("Расход записан в таблицу");syncNow(false);}
                        public void onError(String error){showApiError(error);}
                    });
                }
            }catch(Exception ex){showApiError(ex.toString());}
        }).setNegativeButton("Отмена",null).show();
    }

    private void transferDialog(){
        if(!canFinance()){toast("Нет доступа к финансам");return;}
        LinearLayout form=v();form.setPadding(dp(18),0,dp(18),0);
        Spinner dir=new Spinner(this);dir.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Константин → Игорь","Игорь → Константин"}));form.addView(labelWrap("Направление",dir));
        EditText amt=edit("Сумма");amt.setInputType(InputType.TYPE_CLASS_NUMBER);form.addView(labelWrap("Сумма",amt));
        EditText comment=edit("Комментарий");form.addView(labelWrap("Комментарий",comment));
        new AlertDialog.Builder(this).setTitle("Передать деньги")
                .setMessage("Внутренний перевод не является расходом и не меняет общий баланс компании.")
                .setView(form).setPositiveButton("Передать",(d,w)->{
                    long v=parseLong(amt.getText().toString());if(v<=0)return;
                    if(api==null||!api.hasToken()){showPairingDialog();return;}
                    String direction=dir.getSelectedItem().toString();
                    String from=direction.startsWith("Константин")?"Константин":"Игорь";
                    String to=direction.startsWith("Константин")?"Игорь":"Константин";
                    try{
                        JSONObject b=new JSONObject();b.put("from",from);b.put("to",to);b.put("amount",v);b.put("comment",comment.getText().toString().trim());
                        api.mutate("transfer",b,new ApiClient.Callback(){
                            public void onSuccess(JSONObject json){toast("Передача записана");syncNow(false);}
                            public void onError(String error){showApiError(error);}
                        });
                    }catch(Exception ex){showApiError(ex.toString());}
                }).setNegativeButton("Отмена",null).show();
    }

    // ---------- analytics ----------

    private void showAnalytics(){
        beginScreen(true);appBar("Аналитика","Показатели API за выбранный период");periodSelector();
        String[][] metrics={{"turnover","Оборот"},{"expenses","Расходы"},{"profit","Прибыль"},{"debt","Дебиторка"},{"plan_income","План поступлений"},{"remaining","Осталось получить"}};
        for(int i=0;i<metrics.length;i+=2){LinearLayout row=h();for(int j=i;j<i+2;j++)row.addView(kpiCard("₽",metrics[j][1],kpiValue(metrics[j][0]),"Открыть детализацию",GREEN,"kpi:"+metrics[j][0]),j==i?weight():weightMarginLeft());content.addView(row);spacer(8);}
        sectionTitle("Разделы",null,null);
        String[][] links={{"Объекты","objects"},{"Монтажники","installers"},{"Инженеры","engineers"},{"Менеджеры","managers"},{"Лиды / реклама","kpi:leads"},{"Замеры","surveys"},{"Финансы","finance"}};
        for(String[] link:links)content.addView(clickableInfoRow(link[0],"›",v->navigate(link[1])));
        content.addView(emptyState("Расширенные срезы — следующий этап","Для комбинируемых фильтров, видов работ и группировки по месяцам нужен контракт полной истории API."));
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
        beginScreen(true);appBar("Замеры","Рабочая база API");periodSelector();
        content.addView(kpiCard("▤","Замеры",metric("surveys",false),"За выбранный период",ORANGE,null));
        content.addView(tv("Дата замера не считается датой последнего контакта. Данные касаний пока не передаются API.",11,MUTED,Typeface.NORMAL));
        EditText search=edit("Клиент или последние 4 цифры телефона");content.addView(search);LinearLayout list=v();content.addView(list);
        Runnable rebuild=()->{list.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);for(SurveyItem survey:surveys)if(q.isEmpty()||survey.client.toLowerCase(Locale.ROOT).contains(q)||survey.phone.replaceAll("[^0-9]", "").endsWith(q))list.addView(surveyCard(survey));if(list.getChildCount()==0)list.addView(emptyState("Записей нет","Проверьте фильтр и синхронизацию"));applyInteractionFeedback(list);};search.addTextChangedListener(new SimpleWatcher(rebuild));rebuild.run();
        Button create=primaryOutline("Новый замер");create.setOnClickListener(v->newSurveyDialog());content.addView(create);
    }

    private void showEngineers(){
        beginScreen(true);appBar("Инженеры","Справочник из Google Sheets");
        for(EngineerItem engineer:engineers)content.addView(clickableInfoRow(engineer.name,"Инженер ›",v->navigate("engineer:"+engineer.id)));
        if(engineers.isEmpty())content.addView(emptyState("Не заполнено","В ответе API нет инженеров"));
    }

    private void showManagers(){
        beginScreen(true);appBar("Менеджеры","Показатели API");periodSelector();
        content.addView(clickableInfoRow("Лиды",metric("leads",false),v->navigate("kpi:leads")));
        content.addView(clickableInfoRow("Замеры",metric("surveys",false),v->navigate("surveys")));
        content.addView(clickableInfoRow("Договоры",metric("contracts",false),v->navigate("kpi:contracts")));
        content.addView(emptyState("Персональная аналитика не заполнена","Нужны постоянные Manager_ID, полная история и серверная область доступа. Зарплата и конверсии не рассчитываются из неполной выборки."));
        if(Stage1Rules.isAdmin(apiRole)){Button settings=primaryOutline("Функционал менеджера и Bitrix24");settings.setOnClickListener(v->showBitrixSettings());content.addView(settings);}
    }

    // ---------- calendar / settings ----------

    private void showCalendar(){
        beginScreen(true);appBar("План",calendarDate.toString());
        String[] modes={"Производство","Замеры и выезды","Поступления","Все события"};String[] views={"День","Неделя","Месяц","Список"};
        Button mode=primaryOutline(calendarMode+" ▾");mode.setOnClickListener(v->new AlertDialog.Builder(this).setItems(modes,(d,w)->{calendarMode=modes[w];showCalendar();}).show());content.addView(mode);
        LinearLayout controls=h();Button view=smallButton(calendarView+" ▾",GREEN);view.setOnClickListener(v->new AlertDialog.Builder(this).setItems(views,(d,w)->{calendarView=views[w];showCalendar();}).show());controls.addView(view,weight());
        Button date=smallButton("Дата",BLUE);date.setOnClickListener(v->new DatePickerDialog(this,(picker,y,m,day)->{calendarDate=LocalDate.of(y,m+1,day);showCalendar();},calendarDate.getYear(),calendarDate.getMonthValue()-1,calendarDate.getDayOfMonth()).show());controls.addView(date,weightMarginLeft());content.addView(controls);
        LocalDate from=calendarDate,to=calendarDate;
        if(calendarView.equals("Неделя")){from=calendarDate.minusDays(calendarDate.getDayOfWeek().getValue()-1);to=from.plusDays(6);}else if(calendarView.equals("Месяц")){from=calendarDate.withDayOfMonth(1);to=from.plusMonths(1).minusDays(1);}else if(calendarView.equals("Список")){from=LocalDate.of(1900,1,1);to=LocalDate.of(2200,1,1);}
        final LocalDate rangeStart=from,rangeEnd=to;
        if(!calendarView.equals("Список"))content.addView(tv(from+" — "+to,12,MUTED,Typeface.NORMAL));
        int shown=0;
        if(calendarMode.equals("Производство")||calendarMode.equals("Все события"))for(CalendarItem c:liveCalendar){
            LocalDate start=readDate(c.planStart),end=readDate(c.planEnd);if(start==null)start=readDate(c.windowFrom);if(end==null)end=start;
            if(start==null||end==null||end.isBefore(from)||start.isAfter(to))continue;
            ObjectItem object=findObject(c.objectId);LinearLayout block=v();block.setPadding(dp(12),dp(12),dp(12),dp(12));block.setBackground(round(light(GREEN),18));
            block.addView(tv(object==null?c.objectId:object.address,14,INK,Typeface.BOLD));block.addView(tv(c.installer+" · "+start+" — "+end,12,MUTED,Typeface.NORMAL));
            if(!calendarView.equals("Список")){LinearLayout bar=h();long span=java.time.temporal.ChronoUnit.DAYS.between(from,to)+1;long offset=Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(from,start));long length=Math.min(span-offset,java.time.temporal.ChronoUnit.DAYS.between(start.isBefore(from)?from:start,end.isAfter(to)?to:end)+1);if(offset>0)bar.addView(new Space(this),new LinearLayout.LayoutParams(0,dp(10),offset));View strip=new View(this);strip.setBackground(round(GREEN,5));bar.addView(strip,new LinearLayout.LayoutParams(0,dp(10),Math.max(1,length)));long tail=span-offset-length;if(tail>0)bar.addView(new Space(this),new LinearLayout.LayoutParams(0,dp(10),tail));block.addView(bar,lpMatch(dp(10),8));}
            block.setOnClickListener(v->navigate("object:"+c.objectId));content.addView(block,lpMatch(-2,8));shown++;
        }
        if(calendarMode.equals("Замеры и выезды")||calendarMode.equals("Все события"))for(SurveyItem survey:surveys){LocalDate d=readDate(survey.date);if(d==null||d.isBefore(from)||d.isAfter(to))continue;View row=attention("▦",survey.date+" · "+survey.client,survey.phone,BLUE,"survey:"+survey.id);content.addView(row);shown++;}
        if(canFinance()&&(calendarMode.equals("Поступления")||calendarMode.equals("Все события"))){
            if(plannedMetric().startsWith("Не заполнено"))content.addView(emptyState("Финансовый календарь неполный","Есть объекты без графика оплат"));
            for(PaymentItem payment:livePaymentPlan){LocalDate d=readDate(payment.date);if(d==null||d.isBefore(from)||d.isAfter(to))continue;int color=payment.remaining==0?GREEN:d.isBefore(LocalDate.now())?RED:ORANGE;content.addView(attention("₽",payment.date+" · "+payment.stageName,money(payment.remaining)+" · "+payment.status,color,"payments:"+payment.objectId));shown++;}
        }
        if(shown==0)content.addView(emptyState(hasData("calendar")?"Событий в выборке нет":"Недостаточно данных","Смените дату, вид или обновите данные"));
    }

    private void showSettings(){
        beginScreen(true);appBar("Настройки","Профиль и приложение");
        LinearLayout profile=h();profile.addView(avatarView(),new LinearLayout.LayoutParams(dp(54),dp(54)));TextView name=tv(leaderName,20,INK,Typeface.BOLD);name.setPadding(dp(12),0,0,0);profile.addView(name,new LinearLayout.LayoutParams(0,-2,1));profile.setOnClickListener(v->editProfileDialog());content.addView(profile);
        content.addView(clickableInfoRow("Профиль","Имя и фото",v->editProfileDialog()));
        if(Stage1Rules.isAdmin(apiRole))content.addView(clickableInfoRow("Пользователи и роли","Управление доступом",v->unavailable("Пользователи и роли","Изменение ролей требует административного API. Локальная смена роли запрещена.")));
        content.addView(clickableInfoRow("Интерфейс",themeMode,v->themeDialog()));
        for(String key:new String[]{"haptic","animations"}){Switch sw=new Switch(this);sw.setText(key.equals("haptic")?"Виброотклик":"Анимации");sw.setChecked(prefs.getBoolean(key,true));sw.setOnCheckedChangeListener((b,checked)->prefs.edit().putBoolean(key,checked).apply());content.addView(sw);}
        content.addView(clickableInfoRow("Уведомления","Центр событий",v->navigate("kpi:notifications")));
        content.addView(clickableInfoRow("Синхронизация",lastSyncText,v->showSyncDialog()));
        if(Stage1Rules.isAdmin(apiRole))for(String label:new String[]{"Система","Справочники","Интеграции","Финансовые настройки","Резервные копии и обновления","Журнал изменений"}){
            content.addView(clickableInfoRow(label,"Открыть",v->{if(label.equals("Интеграции"))showBitrixSettings();else unavailable(label,"Настройки сервера пока не передаются API. Изменения будут доступны после подключения соответствующего контракта.");}));
        }
        content.addView(infoRow("Версия","6.2.0-stage1"));
        Button sync=primary("Синхронизировать сейчас");sync.setEnabled(!syncInProgress);sync.setOnClickListener(v->syncNow(true));content.addView(sync);
        Button pair=primaryOutline("Подключение устройства");pair.setOnClickListener(v->showPairingDialog());content.addView(pair);
    }

    // ---------- KPI drilldown ----------

    private void showKpiDetail(String key){
        if(key.equals("notifications")){showNotifications();return;}
        if(key.equals("expense_object")){showFinance();moneyDialog("EXPENSE");return;}
        beginScreen(true);String title=humanKpi(key);appBar(title,"Детализация показателя · "+currentPeriod);content.addView(kpiCard("▥",title,kpiValue(key),"Каждая строка ведёт к первичной записи",GREEN,null));sectionTitle("Расшифровка",null,null);
        if(kpiValue(key).equals("Не заполнено")||kpiValue(key).equals("Недостаточно данных")){
            content.addView(emptyState("Недостаточно данных","API не передаёт этот показатель или подтверждение полноты истории за период."));
            if(key.equals("leads")&&Stage1Rules.isAdmin(apiRole)){Button settings=primaryOutline("Настройки лидов / Bitrix24");settings.setOnClickListener(v->showBitrixSettings());content.addView(settings);}return;
        }
        switch(key){
            case "turnover":
                addThreeMini(miniCard("₽","Общий оборот",metric("turnover",true),GREEN,"kpi:turnover_all"),miniCard("↪","Промежуточные",metric("turnoverIntermediate",true),ORANGE,"kpi:turnover_intermediate"),miniCard("✓","Полностью оплаченные",metric("turnoverFinal",true),BLUE,"kpi:turnover_closed"));
                sectionTitle("Последние поступления",null,null);for(MoneyTx t:txs)if(t.type.equals("INCOME"))content.addView(clickableInfoRow(t.sub,"+"+money(t.amount),v->showOperation(t)));break;
            case "turnover_all":case "turnover_intermediate":case "turnover_closed":
                for(MoneyTx t:txs)if(t.type.equals("INCOME") && turnoverMatches(key,t))content.addView(clickableInfoRow(t.sub,t.title+" · "+money(t.amount),v->showOperation(t)));break;
            case "expenses":financeFilter="Расходы";showFinance();break;
            case "profit":
                content.addView(infoRow("Оборот",metric("turnover",true)));content.addView(infoRow("Расходы",metric("expenses",true)));content.addView(infoRow("Операционная прибыль",metric("profit",true)));content.addView(clickableInfoRow("Прибыль по закрытым объектам","Открыть объекты",v->navigate("objects")));break;
            case "planned_objects":
                content.addView(infoRow("Запланированы",String.valueOf(countStatus("Запланирован"))));content.addView(infoRow("Подтверждены",String.valueOf(countStatus("Подтверждён")+countStatus("Подтверждён клиентом"))));content.addView(infoRow("Готовы к монтажу",String.valueOf(countStatus("Готов к монтажу"))));for(ObjectItem o:objects)if(o.status.equals("Запланирован")||o.status.equals("Подтверждён")||o.status.equals("Подтверждён клиентом")||o.status.equals("Готов к монтажу"))content.addView(clickableInfoRow(o.address,o.status,v->navigate("object:"+o.id)));break;
            case "objects_work":
                for(ObjectItem o:objects)if(o.status.equals("В работе"))content.addView(clickableInfoRow(o.address,o.progress+"% · "+o.installers,v->navigate("object:"+o.id)));break;
            case "plan_income":
                int fp=0;for(PaymentItem p:livePaymentPlan)if(p.remaining>0&&!"Просрочено".equals(p.status)){ObjectItem o=findObject(p.objectId);String a=o==null?p.objectId:o.address;content.addView(clickableInfoRow(p.date+" · "+a,p.stageName+" · "+money(p.remaining),v->navigate("payments:"+p.objectId)));fp++;}if(fp==0)content.addView(emptyState("Будущих этапов пока нет","Заполните график оплат в технических заданиях."));break;
            case "debt":
                int fd=0;for(PaymentItem p:livePaymentPlan)if(p.remaining>0&&"Просрочено".equals(p.status)){ObjectItem o=findObject(p.objectId);String a=o==null?p.objectId:o.address;content.addView(attention("!",a,money(p.remaining)+" · просрочка "+p.overdue+" дн.",RED,"payments:"+p.objectId));fd++;}if(fd==0)content.addView(emptyState("Просроченной дебиторки нет","По заполненным этапам оплат просрочек не найдено."));content.addView(infoRow("Правило","Дебиторка = только просроченный неоплаченный остаток этапов ТЗ."));break;
            case "contracts":
                content.addView(infoRow("Логика","Объекты в работе + завершённые в выбранном периоде"));for(ObjectItem o:objects)if(o.status.equals("В работе")||o.status.equals("Завершён")||o.status.equals("Закрыт 100%"))content.addView(clickableInfoRow(o.address,money(o.contract),v->navigate("object:"+o.id)));break;
            case "avg":content.addView(infoRow("Средний чек API",metric("averageCheck",true)));content.addView(emptyState("Срезы не заполнены","Разбивка по видам работ и полная база договоров ещё не передаются API."));break;
            case "leads":content.addView(infoRow("Лидов за период",String.valueOf(liveKpiInt("leads",0))));content.addView(infoRow("Источник","Детализация ведётся на листе «Лиды» и синхронизируется с приложением."));break;
            case "notifications":if(liveAttention.isEmpty())content.addView(emptyState("Уведомлений нет","Критичных событий по текущим данным нет."));else for(AttentionItem a:liveAttention)content.addView(attention("!",a.title,a.subtitle,"red".equals(a.severity)?RED:ORANGE,a.target));break;
            case "expense_object":moneyDialog("EXPENSE");content.addView(infoRow("Действие","Форма расхода открыта"));break;
            default:content.addView(infoRow("Показатель",title));content.addView(clickableInfoRow("Открыть связанный раздел","Перейти",v->navigate(defaultTargetForKpi(key))));
        }
    }

    // ---------- helpers ----------

    private String humanKpi(String k){
        Map<String,String> m=new HashMap<>();
        m.put("turnover","Оборот");m.put("turnover_all","Общий оборот");m.put("turnover_intermediate","Промежуточные платежи");m.put("turnover_closed","Полностью оплаченные объекты");m.put("profit","Прибыль");m.put("objects_work","Объекты в работе");m.put("planned_objects","Запланированные объекты");m.put("leads","Лиды");
        m.put("contracts","Договоры");m.put("avg","Средний чек");m.put("debt","Дебиторка");m.put("expenses","Расходы");
        m.put("plan_income","Планируется поступление");m.put("remaining","Осталось получить");m.put("balance","Общий остаток");
        m.put("accountable","Подотчёт");m.put("notifications","Уведомления");return m.getOrDefault(k,k.replace('_',' '));
    }
    private String kpiValue(String key){
        String apiKey=key;
        switch(key){case "avg":apiKey="averageCheck";break;case "plan_income":return plannedMetric();case "remaining":apiKey="remainingToReceive";break;case "objects_work":apiKey="objectsInWork";break;case "planned_objects":apiKey="plannedObjects";break;case "turnover_all":apiKey="turnover";break;case "turnover_intermediate":apiKey="turnoverIntermediate";break;case "turnover_closed":apiKey="turnoverFinal";break;case "accountable":case "balance":apiKey="totalAccountable";break;}
        return metric(apiKey,!(apiKey.equals("leads")||apiKey.equals("surveys")||apiKey.equals("contracts")||apiKey.equals("objectsInWork")||apiKey.equals("plannedObjects")));
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
        TextView l=tv(label,12,MUTED,Typeface.BOLD);content.addView(l,lpMatch(-2,10));EditText e=edit(label);e.setText(value);content.addView(e,lpMatch(dp(56),4));
        if(label.toLowerCase(Locale.ROOT).contains("дата"))dateOnly(e);return e;
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

    // ---------- interactions ----------

    private int screenBg(){return "Тёмная".equals(themeMode)?Color.rgb(18,24,33):WHITE;}
    private int cardBg(){return "Тёмная".equals(themeMode)?Color.rgb(29,37,48):WHITE;}
    private int softBg(){return "Тёмная".equals(themeMode)?Color.rgb(38,47,59):Color.rgb(246,248,251);}
    private int resolveText(int c){if(!"Тёмная".equals(themeMode))return c;if(c==INK)return Color.rgb(239,244,250);if(c==MUTED)return Color.rgb(170,182,198);return c;}
    private int resolveBg(int c){if(!"Тёмная".equals(themeMode))return c;if(c==WHITE)return cardBg();if(c==Color.rgb(246,248,251)||c==Color.rgb(243,246,249)||c==Color.rgb(250,251,253))return softBg();return c;}
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();}



    private void showPeriodDialog(){String[] ps={"Сегодня","Неделя","Месяц","Квартал","Год"};new AlertDialog.Builder(this).setTitle("Период").setSingleChoiceItems(ps,Arrays.asList(ps).indexOf(currentPeriod),(d,w)->{d.dismiss();selectPeriod(ps[w]);}).show();}

    private void showQuickAddDialog(){
        if(!canNavigate("create")){unavailable("Добавить","Нет разрешения или сервер ещё не поддерживает данные вашей роли");return;}
        List<String> actions=new ArrayList<>(Arrays.asList("Создать объект","Новый замер"));
        if(canFinance())actions.addAll(Arrays.asList("Добавить приход","Добавить расход","Передать деньги"));
        new AlertDialog.Builder(this).setTitle("Добавить").setItems(actions.toArray(new String[0]),(d,w)->{
            switch(actions.get(w)){case "Создать объект":navigate("create");break;case "Новый замер":newSurveyDialog();break;case "Добавить приход":moneyDialog("INCOME");break;case "Добавить расход":moneyDialog("EXPENSE");break;default:transferDialog();}
        }).show();
    }
    private void showScreenMenu(){
        String[] items={"Обновить данные","Настройки","Поделиться сводкой","О приложении"};
        new AlertDialog.Builder(this).setTitle("Тёплая Компания").setItems(items,(d,w)->{if(w==0)syncNow(true);else if(w==1)navigate("settings");else if(w==2)shareSummary();else showAboutDialog();}).show();
    }
    private void showAboutDialog(){new AlertDialog.Builder(this).setTitle("Тёплая Компания 4.0").setMessage("Stage 1 · 6.2.0\n\nНативное Android-приложение. Рабочие данные синхронизируются с Google Sheets через защищённый API.\n\n"+lastSyncText).setPositiveButton("Понятно",null).show();}
    private void shareSummary(){
        if(!canFinance()){toast("Сводка недоступна для вашей роли");return;}
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,"Тёплая Компания — "+currentPeriod+"\nОборот: "+metric("turnover",true)+"\nРасходы: "+metric("expenses",true)+"\nПрибыль: "+metric("profit",true)+"\n"+lastSyncText);startActivity(Intent.createChooser(i,"Поделиться сводкой"));
    }

    private int countStatus(String status){int n=0;for(ObjectItem o:objects)if(o.status.equals(status))n++;return n;}
    private int countInstallerStatus(String st){int n=0;for(InstallerItem i:installers)if(i.status.equals(st))n++;return n;}
    private int totalClosedObjects(){int n=0;for(InstallerItem i:installers)n+=i.closedObjects;return n;}
    private int countConvertedSurveys(){int n=0;for(SurveyItem s:surveys)if(s.converted)n++;return n;}
    private String[] engineerNames(){String[] a=new String[engineers.size()];for(int i=0;i<a.length;i++)a[i]=engineers.get(i).name;return a;}
    private String[] managerNames(){String[] a=new String[managers.size()];for(int i=0;i<a.length;i++)a[i]=managers.get(i).name;return a;}

    private EditText choiceField(String label,String value,String[] choices){EditText e=field(label,value);e.setFocusable(false);e.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(label).setItems(choices,(d,w)->e.setText(choices[w])).show());return e;}
    private EditText taskField(String label,String value){EditText e=field(label,value);if(label.contains("План начала")||label.contains("План окончания")||label.contains("— дата"))dateOnly(e);return e;}
    private View emptyState(String title,String sub){LinearLayout c=v();c.setPadding(dp(16),dp(18),dp(16),dp(18));c.setGravity(Gravity.CENTER);c.setBackground(round(softBg(),15));c.addView(tv(title,14,INK,Typeface.BOLD));c.addView(tv(sub,10,MUTED,Typeface.NORMAL));return c;}
    private View clickableInfoRow(String left,String right,View.OnClickListener click){View r=infoRow(left,right);r.setOnClickListener(click);return r;}
    private View labelWrap(String label,View view){LinearLayout l=v();l.setPadding(0,dp(5),0,dp(5));l.addView(tv(label,10,MUTED,Typeface.BOLD));l.addView(view,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));return l;}
    private String joinNames(Collection<String> c){StringBuilder b=new StringBuilder();for(String x:c){if(b.length()>0)b.append(", ");b.append(x);}return b.toString();}

    private void dial(String phone){try{startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+phone)));}catch(Exception e){toast("Набор номера недоступен");}}
    private void openMap(String address){try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(address)));startActivity(i);}catch(Exception e){toast("Карты не найдены");}}
    private void changeObjectStatus(ObjectItem o){
        String[] st={"Запланирован","Подтверждён","Готов к монтажу","В работе","Приостановлен","Работы завершены","Закрыт 100%","Отменён"};
        new AlertDialog.Builder(this).setTitle("Статус объекта").setSingleChoiceItems(st,Arrays.asList(st).indexOf(o.status),(d,w)->{
            d.dismiss();
            if((st[w].equals("Подтверждён")||st[w].equals("Готов к монтажу")||st[w].equals("В работе"))&&o.planStart.isEmpty()){scheduleObject(o);return;}
            if(api==null||!api.hasToken()){toast("Нет подключения к Google Sheets");return;}
            try{
                JSONObject b=new JSONObject();b.put("objectId",o.id);b.put("status",st[w]);b.put("expectedRevision",o.revision);
                api.mutate("updateObjectStatus",b,new ApiClient.Callback(){
                    public void onSuccess(JSONObject json){toast("Статус обновлён");syncNow(false);}
                    public void onError(String error){showApiError(error);}
                });
            }catch(Exception ex){showApiError(ex.toString());}
        }).show();
    }

    private void showPayments(String objectId){
        ObjectItem o=findObject(objectId);if(o==null){navigate("objects");return;}
        beginScreen(true);appBar("График платежей",o.address);
        content.addView(kpiCard("↓","Получено",money(o.paid),"Фактический приход по объекту",GREEN,null));spacer(7);
        content.addView(kpiCard("◷","Осталось по договору",money(Math.max(0,o.contract-o.paid)),"Договор − фактические поступления",ORANGE,null));

        sectionTitle("Этапы оплат","Из технического задания",null);
        int shown=0;
        for(PaymentItem p:livePaymentPlan){
            if(!objectId.equals(p.objectId))continue;
            content.addView(livePaymentRow(o,p));shown++;
        }
        if(shown==0){
            content.addView(emptyState("График оплат не создан","Откройте техническое задание и добавьте даты и суммы этапов."));
            Button tz=primaryOutline("Открыть ТЗ");tz.setOnClickListener(v->navigate("tech:"+objectId));content.addView(tz);
        }
    }

    private View livePaymentRow(ObjectItem o,PaymentItem p){
        LinearLayout r=h();r.setPadding(dp(10),dp(9),dp(10),dp(9));r.setBackground(round(cardBg(),14));
        LinearLayout x=v();x.addView(tv(p.date+" · "+p.stageName,11,INK,Typeface.BOLD));
        int tint="Просрочено".equals(p.status)?RED:"Оплачено".equals(p.status)?GREEN:MUTED;
        x.addView(tv(p.status+(p.overdue>0?" · "+p.overdue+" дн.":""),9,tint,Typeface.NORMAL));
        x.addView(tv("План: "+money(p.planned)+" · Получено: "+money(p.paid),9,MUTED,Typeface.NORMAL));
        r.addView(x,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView m=tv(money(p.remaining),12,tint,Typeface.BOLD);r.addView(m);
        if(p.remaining>0){
            TextView ok=pillText("+ Оплата",9,WHITE,GREEN);ok.setPadding(dp(8),dp(5),dp(8),dp(5));
            ok.setOnClickListener(v->{
                if(api==null||!api.hasToken()){showPairingDialog();return;}
                EditText amt=edit("Сумма");amt.setInputType(InputType.TYPE_CLASS_NUMBER);amt.setText(String.valueOf(p.remaining));
                new AlertDialog.Builder(this).setTitle("Оплата этапа").setMessage(p.stageName+" · "+p.date).setView(amt)
                        .setPositiveButton("Провести",(d,w)->{
                            long value=parseLong(amt.getText().toString());if(value<=0)return;
                            try{
                                JSONObject b=new JSONObject();b.put("amount",value);b.put("objectId",o.id);b.put("paymentPlanId",p.id);
                                b.put("paymentKind","Промежуточный платёж");b.put("operation","Приход");
                                b.put("recipient","U-KONSTANTIN".equals(api.getUserId())?"Константин":"Игорь");
                                b.put("comment","Оплата по "+p.stageName);
                                api.mutate("addIncome",b,new ApiClient.Callback(){
                                    public void onSuccess(JSONObject json){toast("Платёж записан");syncNow(false);}
                                    public void onError(String error){showApiError(error);}
                                });
                            }catch(Exception ex){showApiError(ex.toString());}
                        }).setNegativeButton("Отмена",null).show();
            });
            LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(34));op.setMargins(dp(8),0,0,0);r.addView(ok,op);
        }
        LinearLayout.LayoutParams pms=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);pms.setMargins(0,0,0,dp(6));r.setLayoutParams(pms);return r;
    }

    private void showPhotoReports(String objectId){
        ObjectItem o=findObject(objectId);if(o==null){navigate("objects");return;}
        beginScreen(true);appBar("Фотоотчёты",o.address);
        sectionTitle("Лента объекта","Google Drive",null);
        int shown=0;
        for(MediaItem m:liveMedia){
            if(!objectId.equals(m.objectId))continue;
            LinearLayout c=h();c.setPadding(dp(10),dp(10),dp(10),dp(10));c.setBackground(round(softBg(),14));
            TextView ph=pillText("▧",26,BLUE,light(BLUE));ph.setGravity(Gravity.CENTER);c.addView(ph,new LinearLayout.LayoutParams(dp(66),dp(66)));
            LinearLayout t=v();t.setPadding(dp(10),0,0,0);t.addView(tv(m.stage.isEmpty()?m.type:m.stage,11,BLUE,Typeface.BOLD));
            t.addView(tv(m.comment.isEmpty()?"Фото объекта":m.comment,11,INK,Typeface.NORMAL));t.addView(tv(m.date,9,MUTED,Typeface.NORMAL));
            c.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            c.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(m.url)));}catch(Exception e){toast("Не удалось открыть файл");}});
            LinearLayout.LayoutParams p=lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT,0);p.setMargins(0,0,0,dp(7));content.addView(c,p);shown++;
        }
        if(shown==0)content.addView(emptyState("Фотоотчётов пока нет","Монтажники или инженер могут добавить фотографии к объекту."));
        Button add=primaryOutline("+ Добавить фото");add.setOnClickListener(v->pickMedia(objectId));content.addView(add);
    }

    private void pickMedia(String objectId){
        pendingMediaObjectId=objectId;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,REQ_PICK_MEDIA);
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_AVATAR && resultCode==RESULT_OK && data!=null && data.getData()!=null){
            Uri selected=data.getData();
            try{getContentResolver().takePersistableUriPermission(selected,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                prefs.edit().putString("avatarUri:"+api.getUserId(),selected.toString()).apply();render();
            }catch(SecurityException e){toast("Не удалось сохранить доступ к фото. Выберите фото через Галерею.");}
            return;
        }
        if(requestCode!=REQ_PICK_MEDIA||resultCode!=RESULT_OK||data==null||data.getData()==null||pendingMediaObjectId==null)return;
        if(api==null||!api.hasToken()){showPairingDialog();return;}
        Uri uri=data.getData();final String objectId=pendingMediaObjectId;pendingMediaObjectId=null;
        new Thread(()->{
        try(InputStream is=getContentResolver().openInputStream(uri);ByteArrayOutputStream bos=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;while((n=is.read(buf))>0){bos.write(buf,0,n);if(bos.size()>6*1024*1024){runOnUiThread(()->toast("Фото больше 6 МБ"));return;}}
            String b64=Base64.encodeToString(bos.toByteArray(),Base64.NO_WRAP);
            JSONObject b=new JSONObject();b.put("objectId",objectId);b.put("base64",b64);b.put("mimeType",getContentResolver().getType(uri));
            b.put("fileName","TK-"+objectId+"-"+System.currentTimeMillis()+".jpg");b.put("mediaType","Фото");b.put("stage","ПРОЦЕСС");b.put("comment","Загружено из приложения");
            api.mutate("uploadMedia",b,new ApiClient.Callback(){
                public void onSuccess(JSONObject json){toast("Фото загружено в Google Drive");syncNow(false);}
                public void onError(String error){showApiError(error);}
            });
        }catch(Exception e){runOnUiThread(()->showApiError("Не удалось прочитать фото"));}
        },"tk4-media").start();
    }

    private void installerPaymentDialog(InstallerItem i){unavailable("Выплата монтажнику","Используйте реальный расход с привязкой к Installer_ID после поддержки этой операции сервером.");}
    private void showNewInstallerDialog(){unavailable("Недоступно","Для этого действия пока нет безопасного серверного API. Данные не изменены.");}

    private boolean financeMatches(MoneyTx t){if(financeFilter.equals("Все"))return true;if(financeFilter.equals("Приходы"))return t.type.equals("INCOME");if(financeFilter.equals("Расходы"))return t.type.equals("EXPENSE");if(financeFilter.equals("Переводы"))return t.type.equals("TRANSFER");if(financeFilter.equals("Маркетинг"))return t.sub.contains("Маркетинг")||t.title.contains("Директ")||t.title.contains("Реклама");if(financeFilter.equals("Монтажники"))return t.sub.contains("монтаж")||findInstallerByName(t.sub)!=null;return !t.sub.equals("Без привязки");}
    private boolean analyticsObjectMatches(ObjectItem o){if(analyticsFilter.equals("Все объекты")||analyticsFilter.startsWith("По "))return true;return o.status.equals(analyticsFilter);}

    private InstallerItem findInstallerByName(String n){for(InstallerItem i:installers)if(i.name.equals(n))return i;return null;}
    private SurveyItem findSurvey(String id){for(SurveyItem s:surveys)if(s.id.equals(id))return s;return null;}
    private EngineerItem findEngineer(String id){for(EngineerItem e:engineers)if(e.id.equals(id))return e;return null;}
    private ManagerItem findManager(String id){for(ManagerItem m:managers)if(m.id.equals(id))return m;return null;}

    private View surveyCard(SurveyItem survey){return clickableInfoRow(survey.client,survey.date+" · "+(survey.converted?"Объект создан":"Замер"),v->navigate("survey:"+survey.id));}
    private void showSurveyDetail(String id){SurveyItem x=findSurvey(id);if(x==null){navigate("surveys");return;}beginScreen(true);appBar("Замер",x.client);content.addView(infoRow("Телефон",x.phone));content.addView(infoRow("Дата замера",x.date));content.addView(infoRow("Потенциал договора","Не заполнено"));content.addView(infoRow("Последнее касание","Не заполнено"));Button call=primaryOutline("Позвонить клиенту");call.setOnClickListener(v->{dial(x.phone);});content.addView(call);spacer(7);Button conv=primary(x.converted?"Уже переведён в объект":"Перевести замер в объект");conv.setEnabled(!x.converted);conv.setOnClickListener(v->convertSurvey(x));content.addView(conv);}
    private void convertSurvey(SurveyItem x){
        if(api==null||!api.hasToken()){showPairingDialog();return;}
        try{JSONObject b=new JSONObject();b.put("surveyId",x.id);api.mutate("convertSurveyToObject",b,new ApiClient.Callback(){
            public void onSuccess(JSONObject json){x.converted=true;toast("Замер переведён в объект");syncNow(false);String oid=json.optString("objectId");if(!oid.isEmpty())navigate("object:"+oid);}
            public void onError(String error){showApiError(error);}
        });}catch(Exception e){showApiError(e.toString());}
    }
    private void newSurveyDialog(){
        LinearLayout f=v();f.setPadding(dp(18),0,dp(18),0);EditText n=edit("Клиент");EditText ph=edit("Телефон");EditText ad=edit("Адрес");EditText wt=edit("Вид работ");
        f.addView(labelWrap("Клиент",n));f.addView(labelWrap("Телефон",ph));f.addView(labelWrap("Адрес",ad));f.addView(labelWrap("Вид работ",wt));
        new AlertDialog.Builder(this).setTitle("Новый замер").setView(f).setPositiveButton("Добавить",(d,w)->{
            if(n.getText().toString().trim().isEmpty())return;if(api==null||!api.hasToken()){showPairingDialog();return;}
            try{JSONObject b=new JSONObject();b.put("client",n.getText().toString().trim());b.put("phone",ph.getText().toString().trim());b.put("address",ad.getText().toString().trim());b.put("workType",wt.getText().toString().trim());b.put("date",LocalDate.now().toString());
                api.mutate("addSurvey",b,new ApiClient.Callback(){public void onSuccess(JSONObject json){toast("Замер записан в таблицу");syncNow(false);navigate("surveys");}public void onError(String error){showApiError(error);}});
            }catch(Exception e){showApiError(e.toString());}
        }).setNegativeButton("Отмена",null).show();
    }

    private void showEngineerDetail(String id){
        EngineerItem engineer=findEngineer(id);if(engineer==null){navigate("engineers");return;}
        beginScreen(true);appBar(engineer.name,"Инженер · "+id);content.addView(emptyState("Недостаточно данных","Персональная сводка и привязка объектов по Engineer_ID пока не передаются API."));
        Button objectsButton=primaryOutline("Открыть объекты и ТЗ");objectsButton.setOnClickListener(v->navigate("objects"));content.addView(objectsButton);
    }
    private void showManagerDetail(String id){ManagerItem m=findManager(id);if(m==null){navigate("managers");return;}beginScreen(true);appBar(m.name,"Менеджер");content.addView(infoRow("Лиды",String.valueOf(m.leads)));content.addView(infoRow("Замеры",String.valueOf(m.surveys)));content.addView(infoRow("Договоры",String.valueOf(m.contracts)));content.addView(infoRow("Монтажи",String.valueOf(m.installations)));content.addView(infoRow("Конверсия лид → договор",m.leads==0?"0%":(m.contracts*100/m.leads)+"%"));Button lead=primaryOutline("+ Новая заявка");lead.setOnClickListener(v->newLeadDialog());content.addView(lead);}
    private void showNewEngineerDialog(){unavailable("Недоступно","Для этого действия пока нет безопасного серверного API. Данные не изменены.");}
    private void showNewManagerDialog(){unavailable("Недоступно","Для этого действия пока нет безопасного серверного API. Данные не изменены.");}
    private void newLeadDialog(){LinearLayout f=v();f.setPadding(dp(18),0,dp(18),0);EditText n=edit("Клиент");EditText ph=edit("Телефон");EditText src=edit("Источник");f.addView(labelWrap("Клиент",n));f.addView(labelWrap("Телефон",ph));f.addView(labelWrap("Источник",src));new AlertDialog.Builder(this).setTitle("Новая заявка").setView(f).setPositiveButton("Сохранить",(d,w)->{if(api==null||!api.hasToken()){showPairingDialog();return;}try{JSONObject b=new JSONObject();b.put("client",n.getText().toString().trim());b.put("phone",ph.getText().toString().trim());b.put("source",src.getText().toString().trim());b.put("date",LocalDate.now().toString());api.mutate("addLead",b,new ApiClient.Callback(){public void onSuccess(JSONObject json){toast("Лид записан в таблицу");syncNow(false);}public void onError(String error){showApiError(error);}});}catch(Exception e){showApiError(e.toString());}}).setNegativeButton("Отмена",null).show();}
    private void syncNow(boolean showMessage){
        if(syncInProgress)return;
        if(api==null||!api.hasToken()){if(showMessage)showPairingDialog();return;}
        final String requestedPeriod=currentPeriod, requestedUser=api.getUserId();
        syncInProgress=true;render();
        api.bootstrap(requestedPeriod,new ApiClient.Callback(){
            public void onSuccess(JSONObject json){
                syncInProgress=false;
                if(isFinishing()||isDestroyed())return;
                if(!requestedUser.equals(api.getUserId())){syncNow(false);return;}
                try{
                    if(!json.optBoolean("ok")||json.optJSONObject("user")==null)throw new Exception("Некорректный ответ сервера");
                    JSONObject user=json.getJSONObject("user");
                    if(!requestedUser.equals(user.optString("id")))throw new Exception("Ответ другого пользователя");
                    if(!Stage1Rules.needsScopedData(user.optString("role")))prefs.edit().putString(cacheKey(requestedPeriod),json.toString()).apply();
                    if(!requestedPeriod.equals(currentPeriod)){syncNow(false);return;}
                    applyBootstrap(json);snapshotPeriod=requestedPeriod;liveSyncOk=true;
                    prefs.edit().putString("apiRole",apiRole).apply();
                    lastSyncText="Обновлено: "+json.optString("serverTime","сейчас");saveDemoState();render();
                }catch(Exception e){lastSyncText="Ошибка обработки данных; показан последний снимок";render();if(showMessage)showApiError("Не удалось прочитать ответ сервера");}
            }
            public void onError(String error){
                syncInProgress=false;if(isFinishing()||isDestroyed())return;
                if(!requestedUser.equals(api.getUserId())){syncNow(false);return;}
                lastSyncText=liveSyncOk?"Нет связи · показаны последние сохранённые данные":"Нет связи · данных пока нет";
                if(!requestedPeriod.equals(currentPeriod)){syncNow(false);return;}
                render();if(showMessage)showApiError(error);
            }
        });
    }

    private void showNewEmployee(){unavailable("Недоступно","Для этого действия пока нет безопасного серверного API. Данные не изменены.");}

    private void showAccountable(String who){
        beginScreen(true);appBar("Подотчёт",who.equals("all")?"Все ответственные":who);
        JSONObject balances=hasData("accountable")?snapshot.optJSONObject("accountable"):null;
        if(balances==null){content.addView(emptyState("Не заполнено","API не передаёт остатки подотчёта"));return;}
        if(who.equals("all")){java.util.Iterator<String> names=balances.keys();while(names.hasNext()){String name=names.next();JSONObject person=balances.optJSONObject(name);String value=person!=null&&person.opt("balance") instanceof Number?money(person.optLong("balance")):"Не заполнено";content.addView(clickableInfoRow(name,value,v->navigate("accountable:"+name)));}return;}
        JSONObject person=balances.optJSONObject(who);content.addView(infoRow("Остаток",person!=null&&person.opt("balance") instanceof Number?money(person.optLong("balance")):"Не заполнено"));
        content.addView(tv("История ниже ограничена последними операциями API.",11,MUTED,Typeface.NORMAL));for(MoneyTx tx:txs)if(who.equals(tx.responsible))content.addView(clickableInfoRow(tx.date+" · "+tx.title,money(tx.amount),v->showOperation(tx)));
        if(person!=null){
            for(String[] row:new String[][]{{"Получено от компании","receivedFromCompany"},{"Потрачено","expenses"},{"Передано другому подотчётному лицу","outgoingTransfers"},{"Вложено собственных средств — за всё время","personalInvested"},{"Возмещено собственных средств","personalReimbursed"}})content.addView(infoRow(row[0],person.opt(row[1]) instanceof Number?money(person.optLong(row[1])):"Не заполнено"));
            if(person.opt("balance") instanceof Number){long outstanding=Stage1Ledger.personalFundsOutstanding(person.optLong("balance"));content.addView(infoRow("Собственных средств вложено — не возмещено",money(outstanding)));if(outstanding>0)content.addView(tv("Отрицательный остаток: расходы компании оплачены собственными средствами. Это не ошибка данных.",12,MUTED,Typeface.NORMAL));}
            Button reimbursement=primaryOutline("Возместить личные средства");reimbursement.setOnClickListener(v->workforceUi().reimburse(who,person));content.addView(reimbursement);
        }
    }

    private void editProfileDialog(){EditText n=edit("Имя");n.setText(leaderName);new AlertDialog.Builder(this).setTitle("Профиль").setView(n).setPositiveButton("Сохранить",(d,w)->{String x=n.getText().toString().trim();if(!x.isEmpty())leaderName=x;saveDemoState();render();}).setNeutralButton("Изменить фото",(d,w)->{
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(pick,REQ_AVATAR);
    }).setNegativeButton("Отмена",null).show();}
    private void themeDialog(){String[] a={"Светлая","Тёмная","Как на устройстве"};new AlertDialog.Builder(this).setTitle("Тема приложения").setSingleChoiceItems(a,Arrays.asList(a).indexOf(themeMode),(d,w)->{themeMode=a[w];if(themeMode.equals("Как на устройстве"))themeMode="Светлая";saveDemoState();d.dismiss();configureSystemBars();render();}).show();}
    private void roleDialog(){unavailable("Недоступно","Для этого действия пока нет безопасного серверного API. Данные не изменены.");}
    private void showDirectoriesDialog(){unavailable("Недоступно","Для этого действия пока нет безопасного серверного API. Данные не изменены.");}
    private void confirmReset(){unavailable("Недоступно","Для этого действия пока нет безопасного серверного API. Данные не изменены.");}

    private String defaultTargetForKpi(String key){if(key.contains("installer")||key.contains("payroll")||key.contains("accrued"))return "installers";if(key.contains("survey"))return "surveys";if(key.contains("object")||key.contains("contract"))return "objects";if(key.contains("expense")||key.contains("balance")||key.contains("accountable"))return "finance";return "analytics";}
    private String kpiValueExtended(String key){return kpiValue(key);}

    private long objectDebt(String objectId){long v=0;for(PaymentItem p:livePaymentPlan)if(objectId.equals(p.objectId)&&"Просрочено".equals(p.status))v+=p.remaining;return v;}
    private long objectPlanned(String objectId){long v=0;for(PaymentItem p:livePaymentPlan)if(objectId.equals(p.objectId)&&!"Просрочено".equals(p.status)&&!"Оплачено".equals(p.status))v+=p.remaining;return v;}
    private String nextPaymentText(String objectId){for(PaymentItem p:livePaymentPlan)if(objectId.equals(p.objectId)&&p.remaining>0)return ("Просрочено".equals(p.status)?"Просрочено: ":"")+money(p.remaining)+" · "+p.date;return "Нет неоплаченных этапов";}
    private long liveKpi(String key,long fallback){return liveSyncOk&&snapshotPeriod.equals(currentPeriod)&&liveKpis.containsKey(key)?liveKpis.get(key):0;}
    private int liveKpiInt(String key,int fallback){return (int)liveKpi(key,fallback);}
    private long actualTurnover(){long v=0;for(MoneyTx t:txs)if("INCOME".equals(t.type))v+=t.amount;return liveKpi("turnover",v);}
    private long actualExpenses(){long v=0;for(MoneyTx t:txs)if("EXPENSE".equals(t.type))v+=t.amount;return liveKpi("expenses",v);}
    private long remainingReceiptsTotal(){long v=0;for(ObjectItem o:objects)if(!o.status.equals("Закрыт 100%")&&!o.status.equals("Отменён"))v+=Math.max(0,o.contract-o.paid);return liveKpi("remainingToReceive",v);}
    private long plannedReceiptsTotal(){return liveKpi("plannedReceipts",0);}
    private long debtTotal(){return liveKpi("debt",0);}
    private int contractCountForPeriod(){return liveKpiInt("contracts",0);}
    private long averageCheck(){return liveKpi("averageCheck",0);}
    private long intermediateTurnover(){return liveKpi("turnoverIntermediate",0);}
    private long closedTurnover(){return liveKpi("turnoverFinal",0);}
    private boolean turnoverMatches(String key,MoneyTx t){if(key.equals("turnover_all"))return true;if(key.equals("turnover_intermediate"))return t.title.contains("Промежуточ");if(key.equals("turnover_closed"))return t.title.contains("Окончательный");return false;}
    private void openObjectByAddress(String address){for(ObjectItem o:objects)if(address.contains(o.address)||o.address.contains(address)){navigate("object:"+o.id);return;}navigate("finance");}
    private void showSyncDialog(){
        String state=liveSyncOk?"Подключено к Google Sheets":"Нет подтверждённой синхронизации";
        String who=api!=null&&api.hasToken()?api.getUserName()+" · "+api.getRole():"Устройство не связано";
        new AlertDialog.Builder(this).setTitle("Синхронизация")
                .setMessage(state+"\n"+who+"\n\n"+lastSyncText+"\n\nСхема: Android ⇄ Apps Script API ⇄ Google Sheets 4.2")
                .setPositiveButton("Синхронизировать",(d,w)->syncNow(true))
                .setNeutralButton("Подключение",(d,w)->showPairingDialog())
                .setNegativeButton("Закрыть",null).show();
    }


    private void showPairingDialog(){
        if(api==null || !ApiClient.isConfigured()){
            new AlertDialog.Builder(this).setTitle("API ещё не развёрнут")
                    .setMessage("Код приложения подготовлен, но в сборку ещё не записан URL Google Apps Script /exec.")
                    .setPositiveButton("Понятно",null).show();
            return;
        }
        LinearLayout form=v();form.setPadding(dp(18),0,dp(18),0);
        Spinner who=new Spinner(this);
        who.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Игорь Филинов","Константин Шумкин"}));
        form.addView(labelWrap("Пользователь",who));
        EditText code=edit("Код подключения из листа «Пользователи API»");
        form.addView(labelWrap("Код подключения",code));
        new AlertDialog.Builder(this).setTitle("Подключить устройство к Google Sheets").setView(form)
                .setPositiveButton("Подключить",(d,w)->{
                    String userId=who.getSelectedItemPosition()==0?"U-IGOR":"U-KONSTANTIN";
                    api.pair(userId,code.getText().toString().trim(),new ApiClient.Callback(){
                        public void onSuccess(JSONObject json){
                            seedDemoData();snapshot=new JSONObject();snapshotPeriod="";liveSyncOk=false;financeGranted=false;
                            apiRole=api.getRole();leaderName=api.getUserName();demoRole=apiRole;history.clear();screen="main";restoreSnapshot();render();
                            toast("Устройство подключено: "+json.optString("name"));
                            syncNow(true);
                        }
                        public void onError(String error){showApiError(error);}
                    });
                }).setNegativeButton("Отмена",null).show();
    }

    private void applyBootstrap(JSONObject root) throws Exception{
        JSONObject user=root.optJSONObject("user");
        if(user==null)throw new Exception("Missing user");
        snapshot=root;
        if(user!=null){apiRole=user.optString("role","");financeGranted=user.optBoolean("finance",false);}
        // Current v2.1 backend does not guarantee row-level scoping; fail closed.
        scopedData=false;
        leaderName=user.optString("name","Профиль");demoRole=apiRole;
        if(Stage1Rules.needsScopedData(apiRole)){
            seedDemoData();snapshot=new JSONObject();return;
        }
        igorBalance=0;konstantinBalance=0;
        if(user!=null){
            leaderName=user.optString("name",leaderName);
            String role=user.optString("role","");
            demoRole="OWNER".equals(role)?"Руководитель":"PARTNER".equals(role)?"Партнёр":role;
        }

        liveKpis.clear();
        JSONObject k=root.optJSONObject("kpis");
        if(k!=null){
            String[] keys={"turnover","turnoverIntermediate","turnoverFinal","expenses","profit","objectsInWork","plannedObjects",
                    "leads","surveys","contracts","averageCheck","debt","plannedReceipts","remainingToReceive",
                    "accountableIgor","accountableKonstantin","totalAccountable"};
            for(String key:keys) if(k.has(key)&&!k.isNull(key)&&k.opt(key) instanceof Number) liveKpis.put(key,k.getLong(key));
        }

        JSONObject acc=root.optJSONObject("accountable");
        if(acc!=null){
            JSONObject i=acc.optJSONObject("Игорь"); if(i!=null)igorBalance=i.optLong("balance",igorBalance);
            JSONObject c=acc.optJSONObject("Константин"); if(c!=null)konstantinBalance=c.optLong("balance",konstantinBalance);
        }

        objects.clear();
        JSONArray oa=root.optJSONArray("objects");
        if(oa!=null)for(int x=0;x<oa.length();x++){
            JSONObject o=oa.getJSONObject(x);
            String manager=o.optString("responsible","");
            objects.add(new ObjectItem(
                    o.optString("id"),o.optString("address"),o.optString("client"),o.optString("phone"),
                    o.optString("workType"),o.optString("status"),o.optInt("progress"),o.optLong("contract"),
                    o.optLong("paid"),o.optString("engineer",""),manager,o.optString("brigade","Не назначены"),o.optInt("revision",1)
            ));
            ObjectItem added=objects.get(objects.size()-1);added.planStart=o.optString("planStart");added.planEnd=o.optString("planEnd");
        }

        Map<String,long[]> payroll=new HashMap<>();
        JSONArray pa=root.optJSONArray("payroll");
        if(pa!=null)for(int x=0;x<pa.length();x++){
            JSONObject p=pa.optJSONObject(x);if(p==null)continue;
            String id=p.optString("Installer_ID","");
            long[] vals=payroll.computeIfAbsent(id,z->new long[2]);
            vals[0]+=jsonLong(p,"Начислено"); vals[1]+=jsonLong(p,"Выплачено");
        }

        installers.clear(); engineers.clear();
        JSONArray ea=root.optJSONArray("employees");
        if(ea!=null)for(int x=0;x<ea.length();x++){
            JSONObject e=ea.getJSONObject(x);
            if(!"true".equalsIgnoreCase(e.optString("active","true")) && !"TRUE".equals(e.optString("active")))continue;
            String role=e.optString("role","");
            if(role.toLowerCase(Locale.ROOT).contains("монтаж")||role.equalsIgnoreCase("НАЁМНИК")||e.optString("workerKind").equals("HIRED")){
                long[] pp=payroll.getOrDefault(e.optString("id"),new long[2]);
                installers.add(new InstallerItem(e.optString("id"),e.optString("name"),0,0,0,pp[0],pp[1],0,"","Не заполнено"));
                installers.get(installers.size()-1).hired=role.equalsIgnoreCase("НАЁМНИК")||e.optString("workerKind").equals("HIRED");
            }
            if(role.toLowerCase(Locale.ROOT).contains("инжен")||role.toLowerCase(Locale.ROOT).contains("партн")){
                engineers.add(new EngineerItem(e.optString("id"),e.optString("name"),0,0,0,0));
            }
        }

        managers.clear();

        txs.clear();
        JSONArray income=root.optJSONArray("income");
        if(income!=null)for(int x=0;x<income.length();x++){
            JSONObject t=income.getJSONObject(x);
            String type="Возврат клиенту".equals(t.optString("operation"))?"EXPENSE":"INCOME";
            MoneyTx tx=new MoneyTx(type,t.optString("paymentKind","Приход"),t.optString("object"),t.optLong("amount"));tx.id=t.optString("id");tx.objectId=t.optString("objectId");tx.date=t.optString("date");tx.responsible=t.optString("recipient");tx.comment=t.optString("comment");txs.add(tx);
        }
        JSONArray ex=root.optJSONArray("expenses");
        if(ex!=null)for(int x=0;x<ex.length();x++){
            JSONObject t=ex.getJSONObject(x);
            String type="Перевод подотчёта".equals(t.optString("type"))?"TRANSFER":"EXPENSE";
            String sub=t.optString("object");
            if(sub.isEmpty())sub=t.optString("description");
            MoneyTx tx=new MoneyTx(type,t.optString("article",t.optString("type")),sub,t.optLong("amount"));tx.id=t.optString("id");tx.objectId=t.optString("objectId");tx.date=t.optString("date");tx.responsible=t.optString("responsible");tx.comment=t.optString("description");txs.add(tx);
        }

        surveys.clear();
        JSONArray sa=root.optJSONArray("surveys");
        if(sa!=null)for(int x=0;x<sa.length();x++){
            JSONObject z=sa.optJSONObject(x);if(z==null)continue;
            surveys.add(new SurveyItem(
                    z.optString("Survey_ID"),z.optString("Клиент"),z.optString("Телефон"),
                    z.optString("Дата"),daysSince(z.optString("Дата")),0,
                    "TRUE".equalsIgnoreCase(z.optString("ConvertedToObject"))||"true".equalsIgnoreCase(z.optString("ConvertedToObject"))
            ));
        }

        techTasks.clear();
        JSONArray tt=root.optJSONArray("techTasks");
        if(tt!=null)for(int x=0;x<tt.length();x++){
            JSONObject z=tt.optJSONObject(x);if(z==null)continue;
            String objectId=z.optString("Object_ID");if(objectId.isEmpty())continue;
            TechTask t=new TechTask(objectId);t.saved=true;techTasks.put(objectId,t);
        }
        JSONArray assignments=root.optJSONArray("assignments");
        if(assignments!=null)for(int x=0;x<assignments.length();x++){
            JSONObject z=assignments.optJSONObject(x);if(z==null)continue;
            TechTask t=techTasks.computeIfAbsent(z.optString("Object_ID"),TechTask::new);
            String name=z.optString("Монтажник");if(!name.isEmpty())t.installers.put(name,jsonLong(z,"Согласованная сумма"));
            t.saved=true;
        }
        JSONArray dp=root.optJSONArray("dayPlans");
        if(dp!=null)for(int x=0;x<dp.length();x++){
            JSONObject z=dp.optJSONObject(x);if(z==null)continue;
            TechTask t=techTasks.computeIfAbsent(z.optString("Object_ID"),TechTask::new);
            int n=(int)jsonLong(z,"День №");String task=z.optString("Задача");String q=z.optString("План объём");
            if(n==1){if(t.day1a==null||t.day1a.isEmpty())t.day1a=task+" — "+q;else t.day1b=task+" — "+q;}
            if(n==2){if(t.day2a==null||t.day2a.isEmpty())t.day2a=task+" — "+q;else t.day2b=task+" — "+q;}
            t.saved=true;
        }

        livePaymentPlan.clear();
        JSONArray pp=root.optJSONArray("paymentPlan");
        if(pp!=null)for(int x=0;x<pp.length();x++){
            JSONObject p=pp.optJSONObject(x);if(p==null)continue;
            livePaymentPlan.add(new PaymentItem(p.optString("id"),p.optString("objectId"),p.optString("plannedDate"),
                    p.optString("status"),p.optString("stageName"),p.optLong("plannedAmount"),p.optLong("actualPaid"),
                    p.optLong("remaining"),p.optInt("overdueDays")));
        }

        liveCalendar.clear();
        JSONArray ca=root.optJSONArray("calendar");
        if(ca!=null)for(int x=0;x<ca.length();x++){JSONObject c=ca.optJSONObject(x);if(c==null)continue;liveCalendar.add(new CalendarItem(c.optString("Calendar_ID"),c.optString("Object_ID"),c.optString("Installer_ID"),c.optString("Монтажник"),c.optString("Окно начала — от"),c.optString("Окно начала — до"),c.optString("План начала"),c.optString("План окончания"),c.optString("Статус")));}

        liveMedia.clear();
        JSONArray mm=root.optJSONArray("media");
        if(mm!=null)for(int x=0;x<mm.length();x++){
            JSONObject m=mm.optJSONObject(x);if(m==null)continue;
            liveMedia.add(new MediaItem(m.optString("Media_ID"),m.optString("Object_ID"),m.optString("Дата"),
                    m.optString("Тип"),m.optString("Этап"),m.optString("URL"),m.optString("Комментарий")));
        }

        liveAttention.clear();
        JSONArray aa=root.optJSONArray("attention");
        if(aa!=null)for(int x=0;x<aa.length();x++){
            JSONObject a=aa.optJSONObject(x);if(a==null)continue;
            liveAttention.add(new AttentionItem(a.optString("severity"),a.optString("title"),a.optString("subtitle"),a.optString("target")));
        }
    }

    private long jsonLong(JSONObject o,String key){
        Object v=o.opt(key);if(v==null)return 0;
        try{return Math.round(Double.parseDouble(String.valueOf(v).replace(" ","").replace("\u00a0","").replace(",",".")));}catch(Exception e){return 0;}
    }
    private int daysSince(String d){
        try{
            LocalDate x;
            if(d.contains("."))x=LocalDate.parse(d,DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            else x=LocalDate.parse(d.substring(0,10));
            return (int)java.time.temporal.ChronoUnit.DAYS.between(x,LocalDate.now());
        }catch(Exception e){return 0;}
    }
    private void showApiError(String error){
        new AlertDialog.Builder(this).setTitle("Ошибка синхронизации").setMessage(error).setPositiveButton("Закрыть",null).show();
    }
    private void setBusy(Button b,boolean busy){b.setEnabled(!busy);b.setText(busy?"Сохраняю…":b.getText().toString().replace("Сохраняю…","Сохранить"));}
    private ObjectItem findObjectByAddress(String address){for(ObjectItem o:objects)if(o.address.equals(address))return o;return null;}

    private void loadDemoState(){
        themeMode=prefs.getString("theme","Светлая");
        leaderName=prefs.getString("leader","Профиль");
        currentPeriod=prefs.getString("period","Месяц");
        selectedCalendarDay=LocalDate.now().getDayOfMonth();
        // Legacy state is retained on disk, never interpreted as verified server data.
        seedDemoData();
    }
    private void saveDemoState(){
        prefs.edit().putString("theme",themeMode).putString("leader",leaderName)
                .putString("period",currentPeriod).putInt("calDay",selectedCalendarDay).apply();
    }


    private boolean canFinance(){return Stage1Rules.canSeeCompanyFinance(apiRole,financeGranted);}
    private boolean canNavigate(String target){
        if(target.equals("main")||target.equals("settings")||target.equals("kpi:notifications"))return true;
        if(apiRole.isEmpty()||Stage1Rules.needsScopedData(apiRole)&&!scopedData)return false;
        if(target.equals("newEmployee"))return Stage1Rules.isAdmin(apiRole);
        if(target.equals("finance")||target.equals("analytics")||target.startsWith("accountable:")||target.startsWith("kpi:"))return canFinance();
        return true;
    }
    private boolean hasData(String key){return liveSyncOk&&snapshotPeriod.equals(currentPeriod)&&snapshot.has(key)&&!snapshot.isNull(key);}
    private String metric(String key,boolean currency){
        if(!hasData("kpis")||!liveKpis.containsKey(key))return "Не заполнено";
        if((currentPeriod.equals("Год")||currentPeriod.equals("Квартал"))&&!snapshot.has("coverage"))return "Недостаточно данных";
        return currency?money(liveKpis.get(key)):String.valueOf(liveKpis.get(key));
    }
    private String plannedMetric(){
        if(!hasData("paymentPlan"))return "Не заполнено";
        for(ObjectItem o:objects){if(Stage1Rules.isClosed(o.status)||o.status.equals("Отменён")||o.contract<=o.paid)continue;long unpaid=0;boolean found=false;for(PaymentItem payment:livePaymentPlan)if(o.id.equals(payment.objectId)){found=true;unpaid+=payment.remaining;}if(!found||unpaid!=o.contract-o.paid)return "Не заполнено ⚠";}
        return metric("plannedReceipts",true);
    }
    private String cacheKey(String period){return "snapshot.v1:"+api.getUserId()+":"+period;}
    private void restoreSnapshot(){
        if(api==null||!api.hasToken())return;
        String cached=prefs.getString(cacheKey(currentPeriod),null);if(cached==null)return;
        try{JSONObject json=new JSONObject(cached);if(!api.getUserId().equals(json.getJSONObject("user").optString("id"))||!api.getRole().equals(json.getJSONObject("user").optString("role")))return;
            applyBootstrap(json);snapshotPeriod=currentPeriod;liveSyncOk=true;lastSyncText="Сохранённые данные: "+json.optString("serverTime");
        }catch(Exception ignored){lastSyncText="Сохранённые данные недоступны";}
    }
    private void selectPeriod(String value){
        currentPeriod=value;seedDemoData();snapshot=new JSONObject();snapshotPeriod="";liveSyncOk=false;
        saveDemoState();restoreSnapshot();render();syncNow(false);
    }
    private View avatarView(){
        String uri=prefs.getString("avatarUri:"+(api==null?"":api.getUserId()),"");
        if(!uri.isEmpty())try{ImageView image=new ImageView(this);image.setImageURI(Uri.parse(uri));if(image.getDrawable()!=null){image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(round(softBg(),27));image.setClipToOutline(true);image.setContentDescription("Фото профиля");return image;}}catch(Exception ignored){}
        TextView initials=pillText(initials(leaderName),18,WHITE,GREEN_DARK);initials.setGravity(Gravity.CENTER);return initials;
    }
    private void applyInteractionFeedback(View view){
        if(view.isClickable() && !(view instanceof EditText)){
            if(view.getBackground()!=null&&!(view.getBackground() instanceof RippleDrawable))view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2207844F),view.getBackground(),round(WHITE,22)));
            view.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&prefs.getBoolean("haptic",true))v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);return false;});
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)applyInteractionFeedback(((ViewGroup)view).getChildAt(i));
    }
    private void unavailable(String title,String reason){new AlertDialog.Builder(this).setTitle(title).setMessage(reason).setPositiveButton("Понятно",null).show();}
    private void showBitrixSettings(){
        if(!Stage1Rules.isAdmin(apiRole))return;
        beginScreen(true);appBar("Настройки лидов / Bitrix24","Интеграция через backend");
        content.addView(infoRow("Статус","Не подключено"));content.addView(infoRow("Последняя синхронизация","Не заполнено"));
        content.addView(infoRow("Настройка функционала менеджера","Требуется серверный контракт и сопоставление пользователей"));
        for(String title:new String[]{"Подключить / переподключить","Синхронизировать сейчас","Стадии и источники","Менеджеры и рекламные каналы","Правила первого контакта"})content.addView(clickableInfoRow(title,"Требуется настройка backend",v->unavailable(title,"Секреты Bitrix24 хранятся только на сервере. Контракт ещё не активирован.")));
    }
    private void showNotifications(){
        beginScreen(true);appBar("Все уведомления","Центр событий");
        String[] filters={"Все","Объекты","Финансы","Планируемые поступления","Монтажники","Дебиторка","Лиды","Замеры","ТЗ и ошибки данных","Просрочки","Изменения финансовых операций"};
        Button filter=primaryOutline(attentionFilter+" ▾");filter.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Категория").setItems(filters,(d,w)->{attentionFilter=filters[w];showNotifications();}).show());content.addView(filter);
        int shown=0;for(AttentionItem a:liveAttention){if(!attentionFilter.equals("Все")&&!eventCategory(a).equals(attentionFilter)&&!(attentionFilter.equals("Просрочки")&&(a.title+" "+a.subtitle).toLowerCase(Locale.ROOT).contains("просроч")))continue;
            if(a.target!=null&&!a.target.isEmpty()&&!canNavigate(a.target))continue;
            content.addView(attention("!",a.title,a.subtitle,"red".equals(a.severity)?RED:ORANGE,a.target));shown++;}
        if(shown==0)content.addView(emptyState(hasData("attention")?"Событий в категории нет":"Недостаточно данных",hasData("attention")?"По последнему ответу сервера":"Сначала обновите данные"));
    }
    private String eventCategory(AttentionItem a){String t=a.target==null?"":a.target;String text=(a.title+" "+a.subtitle).toLowerCase(Locale.ROOT);
        if(text.contains("просроч")&&t.startsWith("payments:"))return "Дебиторка";
        if(t.startsWith("payments:"))return "Планируемые поступления";if(t.startsWith("tech:")||text.contains("данных"))return "ТЗ и ошибки данных";
        if(t.contains("installer"))return "Монтажники";if(t.contains("lead"))return "Лиды";if(t.contains("survey"))return "Замеры";if(t.contains("finance"))return "Финансы";return "Объекты";
    }


    private LocalDate readDate(String text){try{if(text==null||text.isEmpty())return null;if(text.contains("."))return LocalDate.parse(text.substring(0,10),DateTimeFormatter.ofPattern("dd.MM.yyyy"));return LocalDate.parse(text.substring(0,10));}catch(Exception ignored){return null;}}
    private void dateOnly(EditText field){field.setFocusable(false);field.setOnClickListener(v->{LocalDate parsed=readDate(field.getText().toString());LocalDate day=parsed==null?LocalDate.now():parsed;new DatePickerDialog(this,(picker,y,m,d)->field.setText(LocalDate.of(y,m+1,d).toString()),day.getYear(),day.getMonthValue()-1,day.getDayOfMonth()).show();});}
    private void scheduleObject(ObjectItem object){
        if(Stage1Rules.isClosed(object.status))return;
        LocalDate day=LocalDate.now();new DatePickerDialog(this,(picker,y,m,d)->{
            final String value=LocalDate.of(y,m+1,d).toString();
            new AlertDialog.Builder(this).setTitle("Подтвердить дату?").setMessage(value).setPositiveButton("Подтвердить",(dialog,which)->{
                try{JSONObject body=new JSONObject();body.put("objectId",object.id);body.put("planStart",value);body.put("status","Подтверждён");body.put("expectedRevision",object.revision);
                    api.mutate("updateObject",body,new ApiClient.Callback(){public void onSuccess(JSONObject result){syncNow(false);}public void onError(String error){showApiError(error);}});
                }catch(Exception e){showApiError("Не удалось подготовить изменение");}
            }).setNegativeButton("Отмена",null).show();
        },day.getYear(),day.getMonthValue()-1,day.getDayOfMonth()).show();
    }
    private void showOperation(MoneyTx operation){
        LinearLayout details=v();details.setPadding(dp(24),dp(16),dp(24),dp(16));
        details.addView(tv(money(operation.amount),24,INK,Typeface.BOLD));details.addView(tv(operation.date+" · "+operation.title,14,INK,Typeface.NORMAL));
        details.addView(tv(operation.sub,14,INK,Typeface.NORMAL));details.addView(tv("Ответственный: "+(operation.responsible.isEmpty()?"Не заполнено":operation.responsible),13,MUTED,Typeface.NORMAL));details.addView(tv(operation.comment,13,MUTED,Typeface.NORMAL));
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Операция "+operation.id).setView(details).setPositiveButton("Закрыть",null);
        if(!operation.objectId.isEmpty())dialog.setNeutralButton("Объект",(d,w)->navigate("object:"+operation.objectId));
        dialog.setNegativeButton("Корректировка",(d,w)->unavailable("Корректировка недоступна","Сервер ещё не поддерживает изменение с обязательной причиной и журналом «было → стало». Исходная операция сохранена."));dialog.show();
    }

    static final class SimpleWatcher implements TextWatcher{private final Runnable r;SimpleWatcher(Runnable r){this.r=r;}public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){r.run();}public void afterTextChanged(Editable e){}}
    static final class SurveyItem{String id,client,phone,date;int daysSinceContact;long potential;boolean converted;SurveyItem(String id,String client,String phone,String date,int days,long potential,boolean converted){this.id=id;this.client=client;this.phone=phone;this.date=date;this.daysSinceContact=days;this.potential=potential;this.converted=converted;}}
    static final class EngineerItem{String id,name;int activeObjects,planned,overdue,noReport;EngineerItem(String id,String name,int a,int p,int o,int n){this.id=id;this.name=name;this.activeObjects=a;this.planned=p;this.overdue=o;this.noReport=n;}}
    static final class ManagerItem{String id,name;int leads,surveys,contracts,installations;ManagerItem(String id,String name,int l,int s,int c,int i){this.id=id;this.name=name;this.leads=l;this.surveys=s;this.contracts=c;this.installations=i;}}

    private String techTaskText(String objectId){
        ObjectItem object=findObject(objectId);StringBuilder text=new StringBuilder("ТЁПЛАЯ КОМПАНИЯ — ТЕХНИЧЕСКОЕ ЗАДАНИЕ\n");
        text.append("Object_ID: ").append(objectId).append("\n");if(object!=null)text.append(object.address).append("\nДаты: ").append(object.planStart).append(" — ").append(object.planEnd).append("\n");
        JSONArray tasks=snapshot.optJSONArray("techTasks");boolean saved=false;
        if(tasks!=null)for(int index=0;index<tasks.length();index++){JSONObject task=tasks.optJSONObject(index);if(task!=null&&objectId.equals(task.optString("Object_ID"))){saved=true;text.append("TechTask_ID: ").append(task.optString("TechTask_ID")).append("\n").append(task.optString("Комментарий")).append("\n");}}
        if(!saved)text.append("ТЗ не сохранено на сервере. Данные формы в этот документ не включены.\n");
        JSONArray days=snapshot.optJSONArray("dayPlans");if(days!=null)for(int index=0;index<days.length();index++){JSONObject day=days.optJSONObject(index);if(day!=null&&objectId.equals(day.optString("Object_ID")))text.append(day.optString("Дата")).append(" · ").append(day.optString("Задача")).append(" · План: ").append(day.optString("План объём")).append("\n");}
        TechTask task=techTasks.get(objectId);if(task!=null){text.append("Монтажники:\n");for(String name:task.installers.keySet())text.append(name).append("\n");}
        text.append("График платежей:\n");boolean payments=false;for(PaymentItem payment:livePaymentPlan)if(objectId.equals(payment.objectId)){payments=true;text.append(payment.id).append(" · ").append(payment.date).append(" · ").append(payment.stageName).append(" · ").append(money(payment.planned)).append("\n");}if(!payments)text.append("Не заполнено\n");
        return text.toString();
    }
    private Stage1WorkforceUi workforceUi(){return new Stage1WorkforceUi(this,snapshot,api,canFinance(),Stage1Rules.isAdmin(apiRole),()->syncNow(false));}
    private String displayField(JSONObject row,String key){return row.has(key)&&!row.isNull(key)&&!row.optString(key).isEmpty()?row.optString(key):"Не заполнено";}
    private boolean hasPaymentRows(String objectId){for(PaymentItem payment:livePaymentPlan)if(objectId.equals(payment.objectId))return true;return false;}
    private void showSavedTechTask(String objectId){
        ObjectItem object=findObject(objectId);beginScreen(true);appBar("Техническое задание",object==null?objectId:object.address);
        content.addView(tv("Существующее ТЗ доступно для просмотра. Изменение через старый API может пересоздать ID графика оплат; редактирование включится после безопасного серверного контракта.",12,MUTED,Typeface.NORMAL));
        JSONArray tasks=snapshot.optJSONArray("techTasks");if(tasks!=null)for(int n=0;n<tasks.length();n++){JSONObject task=tasks.optJSONObject(n);if(task!=null&&objectId.equals(task.optString("Object_ID"))){content.addView(infoRow("TechTask_ID",task.optString("TechTask_ID")));content.addView(infoRow("Статус",task.optString("Статус")));}}
        sectionTitle("План по дням",null,null);JSONArray days=snapshot.optJSONArray("dayPlans");
        if(days!=null)for(int n=0;n<days.length();n++){JSONObject day=days.optJSONObject(n);if(day!=null&&objectId.equals(day.optString("Object_ID")))content.addView(infoRow(day.optString("Дата")+" · "+day.optString("Задача"),day.optString("План объём")+" / "+day.optString("Факт объём")));}
        TechTask task=techTasks.get(objectId);if(task!=null){sectionTitle("Согласованные суммы",null,null);for(Map.Entry<String,Long> entry:task.installers.entrySet())content.addView(infoRow(entry.getKey(),money(entry.getValue())));}
        content.addView(clickableInfoRow("Ежедневные фото","Открыть ›",v->navigate("photos:"+objectId)));
        content.addView(clickableInfoRow("График оплат","Открыть ›",v->navigate("payments:"+objectId)));
        Button share=primaryOutline("Поделиться ТЗ");share.setOnClickListener(v->shareTechTask(objectId));content.addView(share);
        Button pdf=primaryOutline("PDF / Сохранить");pdf.setOnClickListener(v->Stage1Pdf.export(this,"ТЗ-"+objectId,techTaskText(objectId)));content.addView(pdf);
    }

    static final class TechTask{String objectId;Map<String,Long> installers=new LinkedHashMap<>();String day1a="",day1b="",day2a="",day2b="",note="";long pay1=0,pay2=0,pay3=0;boolean saved=false;TechTask(String id){objectId=id;}}

    // ---------- models ----------
    static final class ObjectItem{
        String planStart="",planEnd="";String id,address,client,phone,workType,status,engineer,manager,installers;int progress,revision;long contract,paid;
        ObjectItem(String id,String address,String client,String workType,String status,int progress,long contract,long paid,String engineer,String manager,String installers){
            this(id,address,client,"",workType,status,progress,contract,paid,engineer,manager,installers,1);
        }
        ObjectItem(String id,String address,String client,String phone,String workType,String status,int progress,long contract,long paid,String engineer,String manager,String installers,int revision){
            this.id=id;this.address=address;this.client=client;this.phone=phone;this.workType=workType;this.status=status;this.progress=progress;this.contract=contract;this.paid=paid;this.engineer=engineer;this.manager=manager;this.installers=installers;this.revision=revision;
        }
    }
    static final class InstallerItem{boolean hired=false;
        String id,name,uniformDate,status;int workDays,daysOff,closedObjects,tools;long accrued,paid;
        InstallerItem(String id,String name,int workDays,int daysOff,int closedObjects,long accrued,long paid,int tools,String uniformDate,String status){this.id=id;this.name=name;this.workDays=workDays;this.daysOff=daysOff;this.closedObjects=closedObjects;this.accrued=accrued;this.paid=paid;this.tools=tools;this.uniformDate=uniformDate;this.status=status;}
    }
    static final class AttentionItem{String severity,title,subtitle,target;AttentionItem(String s,String t,String sub,String target){this.severity=s;this.title=t;this.subtitle=sub;this.target=target;}}
    static final class PaymentItem{String id,objectId,date,status,stageName;long planned,paid,remaining;int overdue;PaymentItem(String id,String objectId,String date,String status,String stageName,long planned,long paid,long remaining,int overdue){this.id=id;this.objectId=objectId;this.date=date;this.status=status;this.stageName=stageName;this.planned=planned;this.paid=paid;this.remaining=remaining;this.overdue=overdue;}}
    static final class MediaItem{String id,objectId,date,type,stage,url,comment;MediaItem(String id,String objectId,String date,String type,String stage,String url,String comment){this.id=id;this.objectId=objectId;this.date=date;this.type=type;this.stage=stage;this.url=url;this.comment=comment;}}
    static final class CalendarItem{String id,objectId,installerId,installer,windowFrom,windowTo,planStart,planEnd,status;CalendarItem(String id,String objectId,String installerId,String installer,String windowFrom,String windowTo,String planStart,String planEnd,String status){this.id=id;this.objectId=objectId;this.installerId=installerId;this.installer=installer;this.windowFrom=windowFrom;this.windowTo=windowTo;this.planStart=planStart;this.planEnd=planEnd;this.status=status;}}
    static final class MoneyTx{
        String id="",objectId="",date="",responsible="",comment="";final String type,title,sub;final long amount;
        MoneyTx(String type,String title,String sub,long amount){this.type=type;this.title=title;this.sub=sub;this.amount=amount;}
    }
}

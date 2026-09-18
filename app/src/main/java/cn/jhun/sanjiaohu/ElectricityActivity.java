package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.app.Dialog;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * 电费查询：原生查询宿舍灯光与空调两块电表的剩余电量（单位：度），余额不足时告警。
 *
 * 数据来源与编号规律移植自 eve_all 桌面版电费查询程序（package dianfei）：
 *   - 接口与参数见 {@link ElectricityApi}（实测）
 *   - 表号规律与 39 栋楼对照表见 {@link ElectricityMeter}（实测）
 *   - 号段表持久化见 {@link ElectricityStore}（与桌面版 meters.json 同格式）
 *   - 会话来自 {@link ElectricityCookie}（App 内 WebView 登录「用电缴费」后落下的 Cookie）
 *
 * 界面按本项目既有约定用 Java 代码手工构建（本项目没有 res/layout），
 * 颜色一律走 {@link ThemePalette} 的语义角色，因此深色模式自动成立。
 */
public final class ElectricityActivity extends Activity {

    private ThemePalette theme;
    private SharedPreferences prefs;
    private ElectricityStore store;
    private List<ElectricityMeter> buildings;

    private String building;
    private int floor=4;
    private String roomNo="07";
    private double threshold=20.0;

    private TextView buildingValue,floorValue,roomValue;
    private LinearLayout banner;
    private TextView bannerText,statusLine,emptyHint;
    private TextView refreshButton;
    private LinearLayout cards;

    /** 空调、灯光两张卡片。 */
    private MeterCard airCard,lightCard;

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int generation=0;
    private boolean busy=false;
    /** onCreate 之后紧跟的那次 onResume 不重复查询。 */
    private boolean firstResume=false;

    // ---------------- 生命周期 ----------------

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        theme=AppTheme.from(this,prefs.getInt("themeColor",0xff2ecbff));
        store=new ElectricityStore(this);
        buildings=store.load();
        readSelection(saved);
        AppTheme.applySystemBars(this,theme);
        setContentView(buildScreen());
        refreshSessionState();
        firstResume=true;
    }

    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);
        out.putString("elecBuilding",building);
        out.putInt("elecFloor",floor);
        out.putString("elecRoom",roomNo);
    }

    @Override protected void onResume(){
        super.onResume();
        // 用户可能刚从「用电缴费」网页登录回来，会话状态要重新判定。
        // 但 onCreate 后紧接着的那次 onResume 不该再查一遍（会同一次启动发两轮请求）。
        if(firstResume){firstResume=false;return;}
        refreshSessionState();
    }

    @Override protected void onDestroy(){
        generation++;
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** 选中的楼/层/房从上次离开时恢复；楼名不存在时回落到第一栋。 */
    private void readSelection(Bundle saved){
        building=saved!=null?saved.getString("elecBuilding",""):prefs.getString("elecBuilding","");
        floor=saved!=null?saved.getInt("elecFloor",prefs.getInt("elecFloor",4)):prefs.getInt("elecFloor",4);
        roomNo=saved!=null?saved.getString("elecRoom",prefs.getString("elecRoom","07")):prefs.getString("elecRoom","07");
        if(findBuilding(building)==null)building=buildings.isEmpty()?"":buildings.get(0).name;
        floor=Math.max(1,Math.min(6,floor));
        if(roomNo==null||roomNo.length()!=2)roomNo="07";
    }

    private void persistSelection(){
        prefs.edit().putString("elecBuilding",building).putInt("elecFloor",floor).putString("elecRoom",roomNo).apply();
    }

    private ElectricityMeter findBuilding(String name){
        if(name==null)return null;
        for(ElectricityMeter m:buildings)if(m.name.equals(name))return m;
        return null;
    }

    private ElectricityMeter currentBuilding(){
        ElectricityMeter m=findBuilding(building);
        return m!=null?m:(buildings.isEmpty()?null:buildings.get(0));
    }

    // ---------------- 界面 ----------------

    private View buildScreen(){
        LinearLayout root=column();
        root.setBackgroundColor(theme.surface);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                         insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });

        // 顶栏
        LinearLayout header=row();
        header.setPadding(dp(18),dp(10),dp(18),dp(10));
        TextView back=action("‹",false,this::finish);
        back.setContentDescription("返回");
        header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout titles=column();
        titles.setPadding(dp(14),0,dp(8),0);
        titles.addView(text("电费查询",20,theme.text,true));
        titles.addView(text("宿舍灯光 / 空调 电表余额",11,theme.muted,false));
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        refreshButton=action("刷新",false,this::query);
        header.addView(refreshButton,new LinearLayout.LayoutParams(dp(56),dp(44)));
        root.addView(header);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content=column();
        content.setPadding(dp(16),dp(4),dp(16),dp(20));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        // 告警横幅
        banner=row();
        banner.setPadding(dp(14),dp(11),dp(14),dp(11));
        banner.addView(dot(),new LinearLayout.LayoutParams(dp(9),dp(9)));
        bannerText=text("",13,theme.text,false);
        LinearLayout.LayoutParams bannerWords=new LinearLayout.LayoutParams(0,-2,1);
        bannerWords.leftMargin=dp(10);
        banner.addView(bannerText,bannerWords);
        banner.setVisibility(View.GONE);
        content.addView(banner);
        gap(content,12);

        // 选择区
        LinearLayout picker=column();
        picker.setPadding(dp(14),dp(12),dp(14),dp(14));
        picker.setBackground(shape(theme.controlSurface,20));
        LinearLayout pickRow=row();
        pickRow.addView(selector("宿舍楼",true),new LinearLayout.LayoutParams(0,-2,1.35f));
        LinearLayout.LayoutParams mid=new LinearLayout.LayoutParams(0,-2,0.62f);mid.leftMargin=dp(9);
        pickRow.addView(selector("楼层",false),mid);
        LinearLayout.LayoutParams last=new LinearLayout.LayoutParams(0,-2,0.72f);last.leftMargin=dp(9);
        pickRow.addView(selectorRoom(),last);
        picker.addView(pickRow);
        gap(picker,12);
        statusLine=text("",12,theme.muted,false);
        statusLine.setLineSpacing(dp(3),1f);
        picker.addView(statusLine);
        content.addView(picker);
        gap(content,14);

        // 两张电表卡片
        cards=column();
        airCard=new MeterCard("空调电费",0xff2f6fed);
        lightCard=new MeterCard("灯光电费",0xfff08a24);
        cards.addView(airCard.view,new LinearLayout.LayoutParams(-1,-2));
        gap(cards,12);
        cards.addView(lightCard.view,new LinearLayout.LayoutParams(-1,-2));
        content.addView(cards);

        emptyHint=text("",12,theme.muted,false);
        emptyHint.setLineSpacing(dp(4),1f);
        emptyHint.setVisibility(View.GONE);
        gap(content,14);
        content.addView(emptyHint);

        gap(content,14);
        content.addView(text("灯光与空调是两套独立编号，同一栋楼编号不同。\n"
            +"中间是两个连字符，写成单个会查询失败。",11,theme.muted,false));
        return root;
    }

    /** 宿舍楼 + 楼层两个下拉的标签与取值区。 */
    private View selector(String title,boolean isBuilding){
        LinearLayout box=column();
        box.addView(text(title,11,theme.muted,false));
        gap(box,5);
        TextView value=text("—",14,theme.text,true);
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setPadding(dp(11),dp(10),dp(11),dp(10));
        value.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x22000000),
            shape(theme.entrySurface,12),shape(theme.rippleMask,12)));
        value.setFocusable(true);
        value.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(value,new LinearLayout.LayoutParams(-1,-2));
        if(isBuilding){buildingValue=value;value.setOnClickListener(v->chooseBuilding());value.setContentDescription("选择宿舍楼");}
        else{floorValue=value;value.setOnClickListener(v->chooseFloor());value.setContentDescription("选择楼层");}
        return box;
    }

    /** 寝室号下拉。与 eve_all 一致：范围 01~25，另加该层手写寝室。 */
    private View selectorRoom(){
        LinearLayout box=column();
        box.addView(text("寝室号",11,theme.muted,false));
        gap(box,5);
        roomValue=text("—",14,theme.text,true);
        roomValue.setSingleLine(true);
        roomValue.setGravity(Gravity.CENTER_VERTICAL);
        roomValue.setPadding(dp(11),dp(10),dp(11),dp(10));
        roomValue.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x22000000),
            shape(theme.entrySurface,12),shape(theme.rippleMask,12)));
        roomValue.setFocusable(true);
        roomValue.setContentDescription("选择寝室号");
        roomValue.setOnClickListener(v->chooseRoom());
        box.addView(roomValue,new LinearLayout.LayoutParams(-1,-2));
        return box;
    }

    /** 一张电表卡片：左色条 + 状态胶囊 + 读数 + 表号 + 告警条。 */
    private final class MeterCard {
        final String title;
        final int accent;
        final LinearLayout view;
        final TextView value,unit,pill,detail,warn;
        boolean hasData=false;

        MeterCard(String title,int accent){
            this.title=title;
            this.accent=accent;
            view=column();
            view.setPadding(dp(16),dp(14),dp(16),dp(14));
            view.setBackground(shape(theme.controlSurface,20));

            LinearLayout head=row();
            View bar=new View(ElectricityActivity.this);
            bar.setBackground(shape(accent,3));
            head.addView(bar,new LinearLayout.LayoutParams(dp(4),dp(20)));
            TextView name=text(title,15,theme.text,true);
            name.setPadding(dp(10),0,0,0);
            head.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            pill=text("尚未查询",11,theme.muted,true);
            pill.setPadding(dp(10),dp(4),dp(10),dp(4));
            pill.setBackground(shape(theme.surface,20));
            head.addView(pill);
            view.addView(head);

            gap(view,10);
            LinearLayout reading=row();
            value=text("--",34,theme.muted,true);
            reading.addView(value);
            unit=text("度",13,theme.muted,false);
            unit.setPadding(dp(6),dp(14),0,0);
            reading.addView(unit);
            view.addView(reading);
            gap(view,6);
            detail=text("",11,theme.muted,false);
            detail.setSingleLine(false);
            view.addView(detail);

            warn=text("",12,theme.error,true);
            warn.setPadding(dp(11),dp(8),dp(11),dp(8));
            warn.setBackground(shape(theme.error,10));
            warn.setTextColor(ThemePalette.neutralText(theme.error));
            warn.setVisibility(View.GONE);
            gap(view,10);
            view.addView(warn);
        }

        private void setPill(String label,int background,int foreground){
            pill.setText(label);
            pill.setTextColor(foreground);
            pill.setBackground(shape(background,20));
        }

        void setLoading(){
            hasData=false;
            value.setText("--");
            value.setTextColor(theme.muted);
            unit.setTextColor(theme.muted);
            detail.setText("查询中…");
            detail.setTextColor(theme.muted);
            setPill("查询中",theme.muted,theme.surface);
            warn.setVisibility(View.GONE);
        }

        void setUnavailable(String reason){
            hasData=false;
            value.setText("--");
            value.setTextColor(theme.muted);
            detail.setText(reason);
            detail.setTextColor(theme.muted);
            setPill("未查询",theme.muted,theme.surface);
            warn.setVisibility(View.GONE);
        }

        void setResult(ElectricityApi.Result result,String roomVerify){
            if(result.ok){
                hasData=true;
                double quantity=result.quantityValue();
                value.setText(format(quantity));
                unit.setText(result.unit==null?"度":result.unit);
                detail.setText(roomVerify+"\n电表 "+(result.description==null||result.description.isEmpty()?"?":result.description)
                    +" · "+now());
                if(quantity<threshold){
                    value.setTextColor(theme.error);
                    unit.setTextColor(theme.error);
                    detail.setTextColor(theme.muted);
                    setPill("余额不足",theme.error,ThemePalette.neutralText(theme.error));
                    warn.setText("剩余 "+format(quantity)+" 度，已低于阈值 "+format(threshold)+" 度");
                    warn.setVisibility(View.VISIBLE);
                }else{
                    value.setTextColor(theme.text);
                    unit.setTextColor(theme.muted);
                    detail.setTextColor(theme.muted);
                    setPill("正常",theme.primary,ThemePalette.neutralText(theme.primary));
                    warn.setVisibility(View.GONE);
                }
            }else{
                hasData=false;
                value.setText("--");
                value.setTextColor(theme.muted);
                unit.setTextColor(theme.muted);
                detail.setText(roomVerify==null?"":roomVerify+"\n"+(result.message==null?"查询失败":result.message));
                detail.setTextColor(theme.muted);
                setPill("查询失败",theme.error,ThemePalette.neutralText(theme.error));
                warn.setVisibility(View.GONE);
            }
        }
    }

    private View dot(){
        View v=new View(this);
        v.setBackground(shape(theme.error,5));
        return v;
    }

    // ---------------- 选择 ----------------

    private void chooseBuilding(){
        if(buildings.isEmpty())return;
        final List<String> names=new ArrayList<String>();
        int selected=0;
        for(int i=0;i<buildings.size();i++){
            ElectricityMeter m=buildings.get(i);
            names.add(m.name+"  ·  "+m.supportText());
            if(m.name.equals(building))selected=i;
        }
        showChoices("选择宿舍楼","共 "+buildings.size()+" 栋 · 标签后为该楼支持的电表",names,selected,index->{
            building=buildings.get(index).name;persistSelection();syncSelectorLabels();query();
        });
    }

    private void chooseFloor(){
        List<String> labels=new ArrayList<String>();
        for(int i=1;i<=6;i++)labels.add(i+" 楼");
        showChoices("选择楼层","灯光第三段与房间号首位都由楼层决定",labels,floor-1,index->{
            floor=index+1;persistSelection();syncSelectorLabels();query();
        });
    }

    /** 寝室号候选项：01~25（与 eve_all 默认 roomFrom/roomTo 一致）。 */
    private void chooseRoom(){
        final List<String> options=roomOptions();
        List<String> labels=new ArrayList<String>();
        for(String option:options)labels.add("房间 "+floor+option);
        showChoices("选择寝室号","每层 01 ~ 25",labels,options.indexOf(roomNo),index->{
            roomNo=options.get(index);persistSelection();syncSelectorLabels();query();
        });
    }

    private List<String> roomOptions(){
        List<String> list=new ArrayList<String>();
        for(int i=1;i<=25;i++)list.add(String.format(Locale.US,"%02d",Integer.valueOf(i)));
        if(!list.contains(roomNo))list.add(roomNo);
        Collections.sort(list);
        return list;
    }

    private interface Choice {void accept(int index);}

    /**
     * 一个简单的单选面板。
     *
     * 刻意不复用项目的 UiSheet：它硬绑定 MainActivity（内部调用 a.row/a.label/
     * a.shape/a.palette），本页面是独立 Activity，没有那些成员。外观保持一致。
     */
    private void showChoices(String title,String caption,List<String> labels,int selected,final Choice choice){
        final Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=column();
        panel.setPadding(dp(20),dp(18),dp(20),dp(16));
        panel.setBackground(shape(theme.sheetSurface,24));

        LinearLayout head=row();
        LinearLayout words=column();
        words.addView(text(title,20,theme.text,true));
        TextView subtitle=text(caption,11,theme.muted,false);
        subtitle.setPadding(0,dp(4),0,0);
        words.addView(subtitle);
        head.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=action("×",false,()->dialog.dismiss());
        close.setTextSize(21);
        close.setContentDescription("关闭"+title);
        head.addView(close,new LinearLayout.LayoutParams(dp(42),dp(42)));
        panel.addView(head);
        gap(panel,12);

        ScrollView scroll=new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list=column();
        scroll.addView(list);
        for(int i=0;i<labels.size();i++){
            final int index=i;
            boolean active=i==selected;
            TextView item=text(labels.get(i),14,theme.text,active);
            item.setPadding(dp(14),dp(12),dp(14),dp(12));
            item.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x22000000),
                shape(active?theme.selectedSurface:theme.controlSurface,14),shape(theme.rippleMask,14)));
            item.setFocusable(true);
            item.setContentDescription(labels.get(i)+(active?"，已选中":""));
            item.setOnClickListener(v->{dialog.dismiss();choice.accept(index);});
            list.addView(item,new LinearLayout.LayoutParams(-1,-2));
            if(i!=labels.size()-1)gap(list,7);
        }
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        dialog.setContentView(panel);
        Window window=dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(.24f);
        window.setGravity(Gravity.BOTTOM);
        window.setWindowAnimations(R.style.SheetAnimation);
        dialog.show();
        window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(520)),
            Math.min(dp(560),(int)(getResources().getDisplayMetrics().heightPixels*.7f)));
    }

    private void syncSelectorLabels(){
        buildingValue.setText(building==null||building.isEmpty()?"—":building);
        floorValue.setText(floor+" 楼");
        roomValue.setText(roomNo);
    }

    // ---------------- 查询 ----------------

    /**
     * 重新判定会话：已登录就查一次，未登录就显示登录引导。
     * 只在进入页面和从缴费页返回时调用（onResume），不会自我递归。
     */
    private void refreshSessionState(){
        syncSelectorLabels();
        if(!ElectricityCookie.ready()){showSignedOut();return;}
        banner.setVisibility(View.GONE);
        banner.setOnClickListener(null);
        banner.setFocusable(false);
        emptyHint.setVisibility(View.GONE);
        query();
    }

    /** 未登录（或缺 SESSION）时的界面状态：不发请求，引导去缴费页登录。 */
    private void showSignedOut(){
        busy=false;
        refreshButton.setAlpha(1f);
        banner.setVisibility(View.VISIBLE);
        bannerText.setText("尚未登录用电缴费，点此在校园网页完成登录");
        banner.setOnClickListener(v->openElectricityLogin());
        banner.setFocusable(true);
        banner.setContentDescription("尚未登录用电缴费，点按前往登录");
        airCard.setUnavailable("等待登录");
        lightCard.setUnavailable("等待登录");
        statusLine.setText("查询电表需要 h5cloud 的 SESSION，它只在访问缴费页后下发。");
        emptyHint.setVisibility(View.VISIBLE);
        emptyHint.setText("点此登录：在校园网页里完成一次「用电缴费」登录，"
            +"返回本页即可查询。App 不会读取或保存你的密码。");
    }

    /**
     * 跳去既有的「用电缴费」网页，让用户在那里登录。
     *
     * 刻意不做原生 CAS 密码登录（eve_all 的 CasLogin.kt 会替用户加密提交密码）：
     * 手机上应用内已有正规路径 —— 用户在 WebView 里亲自登录，会话落在系统
     * CookieManager 里。本页面只读那条会话，不接触账号密码。
     */
    private void openElectricityLogin(){
        Intent intent=new Intent(this,IdentityActivity.class);
        intent.putExtra("electricity",true);
        startActivity(intent);
    }

    /**
     * 查询当前房间。灯光与空调各查一次，两次之间按 eve_all 的建议留间隔防风控。
     * 界面标「查询中」，结果回主线程。
     */
    private void query(){
        ElectricityMeter meter=currentBuilding();
        if(meter==null){
            statusLine.setText("号段表为空，请检查 meters.json。");
            return;
        }
        final String lightVerify=meter.lightRoomVerify(floor,roomNo);
        String airVerify=meter.acRoomVerify(floor,roomNo);
        final boolean lightConfigured=lightVerify!=null;
        final boolean airConfigured=airVerify!=null;
        final String lightText=lightVerify;
        final String airText=airVerify;

        String room=floor+roomNo;
        statusLine.setText("房间 "+room
            +(lightConfigured?"   ·   灯光 "+lightText:"   ·   该宿舍楼无照明表")
            +(airConfigured?"   ·   空调 "+airText
                :(meter.acId==null?"   ·   该宿舍楼无空调表":"   ·   该宿舍楼此层空调号段未配置")));

        if(busy)return;
        // 先确认会话：没有 SESSION 就不发注定失败的请求
        // （服务器会回「系统繁忙」，那句话的含义是会话失效，不是余额）。
        final String cookie=ElectricityCookie.current();
        if(cookie==null){showSignedOut();return;}
        busy=true;
        refreshButton.setAlpha(.4f);
        final int id=++generation;

        airCard.setLoading();
        lightCard.setLoading();

        worker.execute(()->{
            ElectricityApi.Result air=null,light=null;
            // 空调在前，与 eve_all 界面的卡片顺序一致。
            if(airConfigured){
                air=ElectricityApi.query(cookie,airText);
                if(lightConfigured)try{Thread.sleep(1200);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            }
            if(lightConfigured)light=ElectricityApi.query(cookie,lightText);
            final ElectricityApi.Result airResult=air,lightResult=light;
            handler.post(()->{
                if(isDestroyed()||id!=generation)return;
                busy=false;
                refreshButton.setAlpha(1f);
                // 会话失效与「表号不对」是两回事：前者要引导重新登录，不能只报查询失败。
                if(sessionExpired(airResult)||sessionExpired(lightResult)){
                    showSignedOut();
                    bannerText.setText("会话已失效，点此重新登录用电缴费");
                    emptyHint.setText("电表会话已过期。重新在校园网页登录一次「用电缴费」，"
                        +"返回本页即可继续查询。");
                    return;
                }
                if(airConfigured)airCard.setResult(airResult,airText);
                else airCard.setUnavailable(meter.acId==null?"该宿舍楼无空调表":"此层空调号段未配置");
                if(lightConfigured)lightCard.setResult(lightResult,lightText);
                else lightCard.setUnavailable("该宿舍楼无照明表");
            });
        });
    }

    /** 与 eve_all 一致：非 JSON 的「系统繁忙」即会话失效。 */
    private boolean sessionExpired(ElectricityApi.Result result){
        return result!=null&&!result.ok
            &&("NOT_JSON".equals(result.code)||"NO_SESSION".equals(result.code));
    }

    private String now(){
        return new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date());
    }

    private static String format(double value){
        return String.format(Locale.US,"%.2f",Double.valueOf(value));
    }

    // ---------------- 视图工具（沿用项目既有写法） ----------------

    private LinearLayout column(){return Ui.column(this);}
    private LinearLayout row(){return Ui.row(this);}
    private void gap(LinearLayout parent,int size){Ui.space(this,parent,size);}
    private int dp(float value){return Ui.dp(this,value);}
    private GradientDrawable shape(int color,int radius){return Ui.shape(this,color,radius);}

    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",0));
        return t;
    }

    private TextView action(String label,boolean primary,Runnable run){
        TextView v=text(label,label.length()==1?24:14,primary?theme.onPrimary:theme.deepAccent,true);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x33000000),
            shape(primary?theme.primary:theme.entrySurface,15),shape(theme.rippleMask,15)));
        v.setOnClickListener(view->run.run());
        return v;
    }
}

package cn.jhun.sanjiaohu;

import android.webkit.CookieManager;

/**
 * 取用 17wanxiao 的会话 Cookie，供 {@link ElectricityApi} 查询电表。
 *
 * 为什么不做原生 CAS 密码登录：
 * eve_all 的 CasLogin.kt 会在本机用 AES 加密密码替用户提交登录。桌面上那是便利功能，
 * 但在手机上应用内已有更合适的路径 —— 用户在 App 内置 WebView 里亲自登录一次
 * （「用电缴费」入口），会话 Cookie 就落在系统 CookieManager 里。
 * 本类只**读取**那条约会，不接触账号密码，也不新建登录通道。
 *
 * 与 eve_all 一致的实测结论：CAS 只会在 authserver 下发 CASTGC、在 hub 下发 JSESSIONID，
 * 而电表查询真正需要的 SESSION 是访问 h5cloud 缴费页时才下发的。
 * 因此这里取的是 h5cloud 那个源的 Cookie；拿不到就说明用户还没走完缴费页那一跳。
 */
final class ElectricityCookie {

    /** 电表接口所在的源（注意端口 18443，Cookie 的 Domain 不含端口，但取用时要带端口）。 */
    static final String AUTHORITY="h5cloud.17wanxiao.com:18443";
    static final String PAGE="https://"+AUTHORITY+"/CloudPayment/bill/selectPayProject.do";

    private ElectricityCookie(){}

    /** 读取当前会话 Cookie；取不到或缺少 SESSION 时返回 null。 */
    static String current(){
        String raw=null;
        try{raw=CookieManager.getInstance().getCookie(PAGE);}catch(Exception ignored){}
        if(raw==null||raw.trim().isEmpty())return null;
        return hasSession(raw)?raw.trim():null;
    }

    /**
     * 这条 Cookie 里是否有真正的电表会话。
     * 只看非空是不够的：没有 SESSION 时服务器会回「系统繁忙」，
     * 与其发一个注定失败的请求，不如先在本地判定「未登录」。
     */
    static boolean hasSession(String cookie){
        if(cookie==null)return false;
        for(String part:cookie.split(";")){
            int eq=part.indexOf('=');
            if(eq<=0)continue;
            if("SESSION".equals(part.substring(0,eq).trim())&&part.substring(eq+1).trim().length()>0)return true;
        }
        return false;
    }

    /** 是否已具备查询条件（供界面决定显示查询按钮还是登录引导）。 */
    static boolean ready(){
        return hasSession(safeCookie());
    }

    private static String safeCookie(){
        try{return CookieManager.getInstance().getCookie(PAGE);}catch(Exception e){return null;}
    }
}

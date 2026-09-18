package cn.jhun.sanjiaohu;

import java.io.*;
import java.net.*;
import java.util.regex.*;

/**
 * 电表查询接口客户端。
 *
 * 移植自 eve_all 的 Api.kt。接口地址、参数、请求头与错误分诊均照抄实测结论，
 * 只把 java.net.http.HttpClient 换成 Android 上可用的 HttpURLConnection。
 *
 * 接口（已实测）：
 *   GET https://h5cloud.17wanxiao.com:18443/CloudPayment/user/getRoomState.do
 *       ?payProId=7033&schoolcode=1862&businesstype=2&roomverify=<表号>
 *
 * 返回：
 *   {"returncode":"100","returnmsg":"SUCCESS","quantity":"52.90",
 *    "quantityunit":"度","canbuy":"true","description":"407"}
 *
 * 关键点：
 *   - quantity 的单位是**度（kWh）**，不是元。52.90 = 52.90 度电。
 *   - returncode=100 成功；FAIL 表示表号不对。
 *   - 灯光和空调只是 roomverify 前缀不同，接口与参数完全一样。
 *   - 中间是两个连字符 `--`，写成 1-15-45-407 会返回 FAIL。
 *   - 必须带 Cookie 和 Referer，否则返回「系统繁忙」。
 *   - 请勿高频请求，短时间内大量查询可能触发风控。
 */
final class ElectricityApi {

    static final String BASE_URL="https://h5cloud.17wanxiao.com:18443/CloudPayment/user/getRoomState.do";
    static final String REFERER="https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/selectPayProject.do";

    /** 固定参数（与 eve_all 的 config.json 默认值一致）。 */
    static final String PAY_PRO_ID="7033";
    static final String SCHOOL_CODE="1862";
    static final String BUSINESS_TYPE="2";

    private static final int CONNECT_TIMEOUT_MS=15000;
    private static final int READ_TIMEOUT_MS=25000;

    /** eve_all 实测使用的 UA（手机端 17wanxiao / 今日校园）。 */
    private static final String USER_AGENT=
        "Mozilla/5.0 (Linux; Android 16; 2510DRK44C Build/BP2A.250605.031.A3; wv) "+
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/143.0.7499.192 "+
        "Mobile Safari/537.36 cpdaily/9.9.20 wisedu/9.9.20";

    /** 一次查询的结果。 */
    static final class Result {
        /** 成功。quantity 单位是度。 */
        final Double quantity;
        final String unit,description;
        final boolean canBuy;
        /** 失败时的原因；code 是 returncode 或本地分类。 */
        final String code,message;
        final boolean ok;

        private Result(boolean ok,Double quantity,String unit,String description,boolean canBuy,String code,String message){
            this.ok=ok;this.quantity=quantity;this.unit=unit;this.description=description;
            this.canBuy=canBuy;this.code=code;this.message=message;
        }
        static Result success(double quantity,String unit,String description,boolean canBuy){
            return new Result(true,Double.valueOf(quantity),unit,description,canBuy,null,null);
        }
        static Result failure(String code,String message){
            return new Result(false,null,null,null,false,code,message);
        }
        double quantityValue(){return quantity==null?0d:quantity.doubleValue();}
    }

    private ElectricityApi(){}

    /**
     * 查询一个电表。**阻塞调用，必须在后台线程使用。**
     * cookie 为空时直接返回失败，不发起请求 —— 否则服务器会回「系统繁忙」，
     * 那句话的真实含义是会话失效，而不是余额为零。
     */
    static Result query(String cookie,String roomVerify){
        if(roomVerify==null||roomVerify.trim().isEmpty())return Result.failure("EMPTY_VERIFY","电表编号为空");
        if(cookie==null||cookie.trim().isEmpty())return Result.failure("NO_SESSION","尚未取得校园会话，请先登录用电缴费");

        String url=BASE_URL
            +"?payProId="+encode(PAY_PRO_ID)
            +"&schoolcode="+encode(SCHOOL_CODE)
            +"&businesstype="+encode(BUSINESS_TYPE)
            +"&roomverify="+encode(roomVerify);

        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent",USER_AGENT);
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("Accept-Language","zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
            connection.setRequestProperty("X-Requested-With","XMLHttpRequest");
            connection.setRequestProperty("Referer",REFERER);
            connection.setRequestProperty("Cookie",cookie.trim());

            int status=connection.getResponseCode();
            InputStream stream=status>=400?connection.getErrorStream():connection.getInputStream();
            if(stream==null)return Result.failure("EMPTY","服务器返回空内容（HTTP "+status+"）");
            String body=readAll(stream);
            if(body.trim().isEmpty())return Result.failure("EMPTY","服务器返回空内容");

            return interpret(body);
        }catch(Exception e){
            return Result.failure("NET","网络错误: "+e.getClass().getSimpleName()+" "+(e.getMessage()==null?"":e.getMessage()));
        }finally{
            if(connection!=null)connection.disconnect();
        }
    }

    /** 解析响应正文。抽出来单独可测。 */
    static Result interpret(String body){
        if(body==null||body.trim().isEmpty())return Result.failure("EMPTY","服务器返回空内容");

        // 非 JSON -> 通常是 "系统繁忙，请稍后重试"，即会话失效
        if(!body.trim().startsWith("{")){
            String hint=(body.contains("系统繁忙")||body.contains("RspBaseVO"))
                ?"会话已失效，请重新登录用电缴费":truncate(body,200);
            return Result.failure("NOT_JSON",hint);
        }

        String code=jsonString(body,"returncode");
        String message=jsonString(body,"returnmsg");
        String quantity=jsonString(body,"quantity");
        String unit=jsonString(body,"quantityunit");
        String description=jsonString(body,"description");
        String canBuy=jsonString(body,"canbuy");

        if(unit==null||unit.trim().isEmpty())unit="度";
        if(description==null)description="";
        boolean purchasable="true".equalsIgnoreCase(canBuy==null?"false":canBuy);

        if("100".equals(code)){
            double value=0d;
            if(quantity!=null){
                try{value=Double.parseDouble(quantity.trim());}catch(NumberFormatException ignored){}
            }
            return Result.success(value,unit,description,purchasable);
        }

        String text;
        if("FAIL".equals(code))text="电表编号无效（roomverify 格式或数值不对）";
        else if(body.contains("系统繁忙"))text="会话已失效，请重新登录用电缴费";
        else if(message!=null&&!message.trim().isEmpty())text=message;
        else text="接口返回 "+(code==null?"未知":code);
        return Result.failure(code==null?"UNKNOWN":code,text);
    }

    private static String encode(String value){
        try{return URLEncoder.encode(value,"UTF-8");}catch(UnsupportedEncodingException e){return value;}
    }

    private static String truncate(String text,int limit){
        return text.length()<=limit?text:text.substring(0,limit);
    }

    private static String readAll(InputStream stream)throws IOException{
        try{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int read;
            while((read=stream.read(buffer))!=-1)out.write(buffer,0,read);
            return out.toString("UTF-8");
        }finally{stream.close();}
    }

    /**
     * 从 JSON 文本里取字符串/数字字段，不引第三方库。
     * eve_all 用同一套正则；这里对转义与 null 的处理保持一致。
     */
    static String jsonString(String text,String key){
        Matcher m=Pattern.compile("\""+Pattern.quote(key)+"\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|null|(-?[0-9.eE+]+))").matcher(text);
        if(!m.find())return null;
        if(m.group(1)!=null&&m.group(1).startsWith("\""))return m.group(2);
        String bare=m.group(3);
        return (bare!=null&&bare.length()>0)?bare:null;
    }
}

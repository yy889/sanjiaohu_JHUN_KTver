package cn.jhun.sanjiaohu;

/**
 * 电表接口响应的分诊检查。
 *
 * 用例里的响应正文取自 eve_all README 记录的**实测返回**，包括那条最容易被误读成
 * 「余额为零」的「系统繁忙」文本（真实含义是会话失效）。
 */
public final class ElectricityApiTest {
    static int checks;
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Case "+checks);}
    static void same(String actual,String expected){checks++;if(expected==null?actual!=null:!expected.equals(actual))throw new AssertionError("Case "+checks+" expected "+expected+" got "+actual);}

    public static void main(String[] args){
        // ---- 实测成功返回：quantity 单位是度，不是元 ----
        String ok="{\"returncode\":\"100\",\"returnmsg\":\"SUCCESS\",\"quantity\":\"52.90\","
            +"\"quantityunit\":\"度\",\"canbuy\":\"true\",\"description\":\"407\"}";
        ElectricityApi.Result success=ElectricityApi.interpret(ok);
        check(success.ok);
        // 解析成数值后与 52.90 相等（格式化只在界面层做，这里比数值）
        check(Math.abs(success.quantityValue()-52.90)<1e-9);
        check(success.quantityValue()==52.9d);
        same(success.unit,"度");
        same(success.description,"407");
        check(success.canBuy);

        // ---- 读数为 0 是**真实的没电了**，不是查询失败 ----
        // 实测：222 房间 code=100 quantity=0.0 canbuy=true
        String empty="{\"returncode\":\"100\",\"returnmsg\":\"SUCCESS\",\"quantity\":\"0.0\","
            +"\"quantityunit\":\"度\",\"canbuy\":\"true\",\"description\":\"222\"}";
        ElectricityApi.Result zero=ElectricityApi.interpret(empty);
        check(zero.ok);
        check(zero.quantityValue()==0d);

        // ---- FAIL 表示表号不对 ----
        // 实测：626 code=FAIL desc=None canbuy=false（6 层只有 25 间）
        String fail="{\"returncode\":\"FAIL\",\"canbuy\":\"false\",\"description\":\"None\"}";
        ElectricityApi.Result failed=ElectricityApi.interpret(fail);
        check(!failed.ok);
        same(failed.code,"FAIL");
        same(failed.message,"电表编号无效（roomverify 格式或数值不对）");

        // ---- 非 JSON 的「系统繁忙」= 会话失效，绝不能当成余额 ----
        ElectricityApi.Result busy=ElectricityApi.interpret("系统繁忙，请稍后重试");
        check(!busy.ok);
        same(busy.code,"NOT_JSON");
        same(busy.message,"会话已失效，请重新登录用电缴费");
        check(!ElectricityApi.interpret("<html>RspBaseVO</html>").ok);

        // ---- 空响应 ----
        check(!ElectricityApi.interpret("").ok);
        check(!ElectricityApi.interpret("   ").ok);
        check(!ElectricityApi.interpret(null).ok);
        same(ElectricityApi.interpret(null).code,"EMPTY");

        // ---- 数字型 quantity（未加引号）也要能读 ----
        ElectricityApi.Result bare=ElectricityApi.interpret("{\"returncode\":\"100\",\"quantity\":194.7,\"description\":\"407\"}");
        check(bare.ok);
        check(bare.quantityValue()==194.7d);
        // quantityunit 缺失时默认「度」
        same(bare.unit,"度");
        check(!bare.canBuy);

        // ---- 字段提取器本身 ----
        same(ElectricityApi.jsonString(ok,"quantity"),"52.90");
        same(ElectricityApi.jsonString(ok,"description"),"407");
        same(ElectricityApi.jsonString(ok,"missing"),null);
        // 显式 null 返回 null，而不是字符串 "null"
        same(ElectricityApi.jsonString("{\"a\":null}","a"),null);
        same(ElectricityApi.jsonString("{\"a\":\"\"}","a"),"");

        // ---- 空表号不发起请求（本地就拦掉）----
        same(ElectricityApi.query("SESSION=x",null).code,"EMPTY_VERIFY");
        same(ElectricityApi.query("SESSION=x","").code,"EMPTY_VERIFY");
        // ---- 没有会话时不发起请求：否则服务器回「系统繁忙」，会被误报成余额异常 ----
        same(ElectricityApi.query(null,"3-3--4-407").code,"NO_SESSION");
        same(ElectricityApi.query("","3-3--4-407").code,"NO_SESSION");
        same(ElectricityApi.query("   ","3-3--4-407").code,"NO_SESSION");

        // ---- 接口常量与 eve_all 实测一致 ----
        same(ElectricityApi.PAY_PRO_ID,"7033");
        same(ElectricityApi.SCHOOL_CODE,"1862");
        same(ElectricityApi.BUSINESS_TYPE,"2");
        check(ElectricityApi.BASE_URL.endsWith("/CloudPayment/user/getRoomState.do"));
        check(ElectricityApi.BASE_URL.startsWith("https://h5cloud.17wanxiao.com:18443/"));
        check(ElectricityApi.REFERER.endsWith("/CloudPayment/bill/selectPayProject.do"));

        System.out.println("Electricity API triage: "+checks+" checks passed");
    }
}

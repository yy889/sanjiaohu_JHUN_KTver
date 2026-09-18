package cn.jhun.sanjiaohu;

public final class IdentityPolicyTest {
    static int checks;
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Case "+checks);}
    public static void main(String[] args){
        check(IdentityPolicy.auth(IdentityPolicy.LOGIN));check(IdentityPolicy.allowed(IdentityPolicy.REPAIR));check(!IdentityPolicy.auth(IdentityPolicy.REPAIR));check(IdentityPolicy.repair(IdentityPolicy.REPAIR));
        for(String value:new String[]{"https://authserver.jhun.edu.cn/authserver/login","https://ehall.jhun.edu.cn/new/index.html","http://ehall.jhun.edu.cn/login?ticket=ST-synthetic","https://hqfw.jhun.edu.cn/wsbx/html/yd/wsbx.html#/wybx"})check(IdentityPolicy.allowed(value));
        for(String value:new String[]{null,"","http://authserver.jhun.edu.cn:8080/authserver/login","javascript:alert(1)","file:///private","content://secrets","https://authserver.jhun.edu.cn.evil.invalid/authserver/login","https://evil.invalid/?https://authserver.jhun.edu.cn","https://u:p@authserver.jhun.edu.cn/authserver/login","https://authserver.jhun.edu.cn:8080/authserver/login","https://jwxt.jhun.edu.cn/cas/login.action","http://hqfw.jhun.edu.cn:81/wsbx","https://hqfw.jhun.edu.cn\\@evil.invalid/"})check(!IdentityPolicy.allowed(value));
        for(String service:new String[]{"https%3A%2F%2Fevil.invalid%2F","javascript%3Aalert%281%29","http%3A%2F%2Fehall.jhun.edu.cn%2Flogin%3Fservice%3Dhttps%253A%252F%252Fevil.invalid%252F","http%3A%2F%2Fuser%40hqfw.jhun.edu.cn%2F"})check(!IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login?service="+service));
        check(!IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/resetPassword"));check(!IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login?service="));
        check(IdentityPolicy.hall("http://ehall.jhun.edu.cn/new/index.html"));check(!IdentityPolicy.hall("https://ehall.jhun.edu.cn/login?ticket=ST-synthetic"));
        check(IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login;jsessionid=SYNTHETIC.route"));
        check(IdentityPolicy.auth("http://authserver.jhun.edu.cn/authserver/login;jsessionid=SYNTHETIC"));
        check(!IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login;jsessionid=SYNTHETIC/elsewhere"));
        check(!IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login;other=1"));
        check(IdentityPolicy.LOGIN.equals(IdentityPolicy.REPAIR_ENTRY));
        check(IdentityPolicy.parse(IdentityPolicy.LOGIN).getFragment()==null);
        try{check(java.net.URLDecoder.decode(IdentityPolicy.parse(IdentityPolicy.LOGIN).getRawQuery().substring("service=".length()),"UTF-8").equals(IdentityPolicy.REPAIR_CALLBACK));}catch(Exception e){throw new AssertionError(e);}
        check(IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login?service=http://hqfw.jhun.edu.cn/wsbx/login/cas%23%2Fwybx"));
        check(!IdentityPolicy.repairLanding("http://hqfw.jhun.edu.cn/wsbx/login/cas?ticket=ST-synthetic#/wybx"));
        check(IdentityPolicy.repairLanding(IdentityPolicy.REPAIR));
        check(!IdentityPolicy.repairLanding("http://hqfw.jhun.edu.cn/wsbx/error"));
        check(IdentityPolicy.repairRoot("http://hqfw.jhun.edu.cn/wsbx/#/wybx"));
        check(!IdentityPolicy.repairRoot(IdentityPolicy.REPAIR_CALLBACK));
        check(!IdentityPolicy.repairRoot(IdentityPolicy.REPAIR));
        check(!IdentityPolicy.repairRoot("http://hqfw.jhun.edu.cn.evil.invalid/wsbx/"));
        check(IdentityPolicy.auth(IdentityPolicy.LOGIN.replace("https:","http:")));
        check(IdentityPolicy.allowed(IdentityPolicy.LOGIN.replace("https:","http:")));
        check(!IdentityPolicy.auth("http://authserver.jhun.edu.cn/authserver/login?service=http%3A%2F%2Fevil.invalid"));
        // ---- 用电缴费/电费查询新增的源白名单 ----
        check(IdentityPolicy.auth(IdentityPolicy.ELECTRICITY_LOGIN));
        check(IdentityPolicy.allowed(IdentityPolicy.ELECTRICITY_SERVICE));
        check(IdentityPolicy.electricity(IdentityPolicy.ELECTRICITY_SERVICE));
        check(IdentityPolicy.electricity("https://h5cloud.17wanxiao.com:18443/CloudPayment/user/getRoomState.do"));
        check(IdentityPolicy.electricity("https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/selectPayProject.do"));
        check(IdentityPolicy.electricity("https://open.17wanxiao.com/authorize"));
        check(IdentityPolicy.electricity("https://wapnew.17wanxiao.com/after"));
        // 电表接口所在的源必须被认作 cloudPayment（带 18443 端口）
        check(IdentityPolicy.cloudPayment("https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/type.do"));
        // 少一个端口就不是那个源
        check(!IdentityPolicy.cloudPayment("https://h5cloud.17wanxiao.com/CloudPayment/bill/type.do"));
        check(!IdentityPolicy.allowed("https://h5cloud.17wanxiao.com/CloudPayment/bill/type.do"));
        // 其他 17wanxiao 子域不在允许之列（只有 hub 是 CAS service 目标）
        check(!IdentityPolicy.allowed("https://evil.17wanxiao.com/bsacs/light.action"));
        check(!IdentityPolicy.allowed("https://17wanxiao.com.evil.invalid/"));
        // 支付宝 H5 是导航目标，不是凭据目标
        check(!IdentityPolicy.allowed("https://mclient.alipay.com/"));
        check(PaymentNavigation.alipayWeb("https://mclient.alipay.com/h5.htm"));
        check(!PaymentNavigation.alipayWeb("http://mclient.alipay.com/h5.htm"));
        check(!PaymentNavigation.alipayWeb("https://mclient.alipay.com:8443/h5.htm"));
        check(!PaymentNavigation.alipayWeb("https://u:p@mclient.alipay.com/h5.htm"));
        check(!PaymentNavigation.alipayWeb("https://mclient.alipay.com.evil.invalid/h5.htm"));
        // intent:// 只在 scheme/package 都受控时才放行，其余一律丢弃
        check(PaymentNavigation.alipayLink("intent://platformapi/startapp?appId=20000056#Intent;scheme=alipays;package=com.eg.android.AlipayGphone;end")!=null);
        check(PaymentNavigation.alipayLink("intent://platformapi/startapp?appId=1#Intent;scheme=alipays;package=com.evil.pay;end")==null);
        check(PaymentNavigation.alipayLink("intent://platformapi/startapp?appId=1#Intent;scheme=alipays;package=com.eg.android.AlipayGphone;SEL;end")==null);
        check(PaymentNavigation.alipayLink("intent://platformapi/startapp?appId=1#Intent;scheme=alipays;package=com.eg.android.AlipayGphone;action=android.intent.action.VIEW;end")!=null);
        check(PaymentNavigation.alipayLink("https://evil.invalid/startapp")==null);
        check(PaymentNavigation.alipayLink("alipays://platformapi/startapp?appId=1")!=null);
        // 隐藏 iframe 只能在支付宝自己的 H5 源里发起
        check(PaymentNavigation.sourceAllowed(true,"https://mclient.alipay.com/h5.htm",false,"GET"));
        check(!PaymentNavigation.sourceAllowed(true,"https://evil.invalid/",false,"GET"));
        check(!PaymentNavigation.sourceAllowed(false,"https://mclient.alipay.com/h5.htm",false,"GET"));
        check(!PaymentNavigation.sourceAllowed(true,"https://mclient.alipay.com/h5.htm",false,"POST"));
        check(!PaymentNavigation.sourceAllowed(true,"https://evil.invalid/",true,"GET"));
        System.out.println("Identity URL policy: "+checks+" checks passed");
    }
}

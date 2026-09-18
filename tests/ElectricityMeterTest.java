package cn.jhun.sanjiaohu;

import java.util.*;

/**
 * 电费查询的编号规律与号段表检查。
 *
 * 断言里的具体表号（3-3--4-407、1-15--45-407 等）取自 eve_all 的实测记录，
 * 不是从本实现反推的期望值 —— 这样它们才能真正约束实现。
 */
public final class ElectricityMeterTest {
    static int checks;
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Case "+checks);}
    static void same(String actual,String expected){checks++;if(expected==null?actual!=null:!expected.equals(actual))throw new AssertionError("Case "+checks+" expected "+expected+" got "+actual);}

    static ElectricityMeter find(String name){
        for(ElectricityMeter m:ElectricityMeter.defaultBuildings())if(m.name.equals(name))return m;
        throw new AssertionError("missing building "+name);
    }

    public static void main(String[] args){
        // ---- 灯光表号：第三段等于楼层，房间号首位也等于楼层 ----
        // 实测：北区3舍 4 楼 07 -> 3-3--4-407
        same(find("北区3舍").lightRoomVerify(4,"07"),"3-3--4-407");
        same(find("北区3舍").lightRoomVerify(1,"07"),"3-3--1-107");
        same(find("北区3舍").lightRoomVerify(6,"07"),"3-3--6-607");
        same(find("北区1舍").lightRoomVerify(4,"07"),"3-1--4-407");
        same(find("北区10舍").lightRoomVerify(2,"13"),"3-10--2-213");
        same(find("北区15A舍").lightRoomVerify(3,"01"),"3-15A--3-301");
        // 食堂公寓照明 id 是 19（来自照明列表）
        same(find("食堂公寓照明").lightRoomVerify(5,"12"),"3-19--5-512");

        // ---- 空调表号：第三段查 acSeg ----
        // 实测：北区3舍 4 楼 07 -> 1-15--45-407
        same(find("北区3舍").acRoomVerify(4,"07"),"1-15--45-407");
        same(find("北区3舍").acRoomVerify(1,"01"),"1-15--42-101");
        same(find("北区3舍").acRoomVerify(2,"01"),"1-15--43-201");
        same(find("北区3舍").acRoomVerify(6,"25"),"1-15--47-625");
        // 南区1舍空调 id 是 1；号段未配置
        same(find("南区1舍").acRoomVerify(4,"07"),null);

        // ---- 中间必须是两个连字符（写成单个服务器会回 FAIL）----
        String light=find("北区3舍").lightRoomVerify(4,"07");
        String air=find("北区3舍").acRoomVerify(4,"07");
        check(light.contains("--"));
        check(air.contains("--"));
        check(!light.replace("--","-").equals(light));
        check(!air.replace("--","-").equals(air));

        // ---- 照明与空调是两套独立编号，同一栋楼编号不同 ----
        ElectricityMeter north3=find("北区3舍");
        check(!north3.lightId.equals(north3.acId));
        same(north3.lightId,"3");
        same(north3.acId,"15");

        // ---- 没有对应表的楼返回 null ----
        same(find("食堂公寓照明").acRoomVerify(4,"07"),null);
        same(find("食堂公寓照明").lightRoomVerify(4,"07"),"3-19--4-407");
        same(find("南区1舍").lightRoomVerify(4,"07"),null);
        // 注意：eve_all 的 meters.json 里「食堂公寓空调」是一条**照明**表（lightId=20, acId=null），
        // 名字叫「空调」但走的是照明大分类。这里照抄该数据，不做「按名字推断」的修正。
        same(find("食堂公寓空调").lightId,"20");
        same(find("食堂公寓空调").acId,null);
        same(find("食堂公寓空调").lightRoomVerify(4,"07"),"3-20--4-407");

        // ---- 号段未配置的楼层返回 null（界面据此提示「未配置」而不是发无效请求）----
        ElectricityMeter north1=find("北区1舍");
        same(north1.acId,"13");
        same(north1.acRoomVerify(4,"07"),null);
        same(north1.lightRoomVerify(4,"07"),"3-1--4-407");

        // ---- 楼栋表完整性：与 eve_all 的 meters.json 同规模（39 栋）----
        List<ElectricityMeter> all=ElectricityMeter.defaultBuildings();
        same(String.valueOf(all.size()),"39");
        Set<String> names=new HashSet<String>();
        for(ElectricityMeter m:all){
            check(names.add(m.name));
            check(m.lightId!=null||m.acId!=null);
        }
        // 北区3舍的号段 42~47 实测齐全
        for(int floor=1;floor<=6;floor++)check(north3.acSeg.containsKey(Integer.valueOf(floor)));
        same(String.valueOf(north3.acSeg.size()),"6");

        // ---- supportText ----
        same(north3.supportText(),"照明 + 空调");
        same(find("北区1舍").supportText(),"照明 + 空调(号段待填)");
        same(find("食堂公寓照明").supportText(),"仅照明");
        same(find("南区1舍").supportText(),"仅空调");

        // ---- 大分类表 ----
        same(ElectricityMeter.CATEGORIES.get("1"),"学生空调/南9-12北18-24空调和照明/商住区");
        same(ElectricityMeter.CATEGORIES.get("3"),"北校区照明");

        // ---- 号段表 JSON 往返（与 eve_all meters.json 同格式）----
        String json=ElectricityStore.toJson(all);
        List<ElectricityMeter> back=ElectricityStore.parse(json);
        check(back!=null);
        same(String.valueOf(back.size()),String.valueOf(all.size()));
        ElectricityMeter roundNorth3=null;
        for(ElectricityMeter m:back)if(m.name.equals("北区3舍"))roundNorth3=m;
        check(roundNorth3!=null);
        same(roundNorth3.lightId,"3");
        same(roundNorth3.acId,"15");
        same(roundNorth3.acRoomVerify(4,"07"),"1-15--45-407");
        for(ElectricityMeter m:back){
            ElectricityMeter original=find(m.name);
            same(m.lightId,original.lightId);
            same(m.acId,original.acId);
            same(String.valueOf(m.acSeg.size()),String.valueOf(original.acSeg.size()));
        }

        // ---- 解析 eve_all 实际格式（含字符串 "null" 与空 acSeg）----
        String fixture="{\"buildings\":["
            +"{\"name\":\"北区3舍\",\"lightId\":\"3\",\"acId\":\"15\",\"acSeg\":{\"4\":\"45\"}},"
            +"{\"name\":\"北区19舍\",\"lightId\":null,\"acId\":\"32\",\"acSeg\":{}},"
            +"{\"name\":\"南区1舍\",\"lightId\":\"null\",\"acId\":\"1\",\"acSeg\":{}}"
            +"]}";
        List<ElectricityMeter> parsed=ElectricityStore.parse(fixture);
        check(parsed!=null);
        same(String.valueOf(parsed.size()),"3");
        same(parsed.get(0).name,"北区3舍");
        same(parsed.get(0).acRoomVerify(4,"07"),"1-15--45-407");
        same(parsed.get(0).acRoomVerify(5,"07"),null);
        // 字符串 "null" 必须当作「没有该表」，而不是编号叫 null
        check(parsed.get(1).lightId==null);
        same(parsed.get(1).acId,"32");
        check(parsed.get(2).lightId==null);
        same(parsed.get(2).acId,"1");

        // ---- 损坏输入不抛异常 ----
        check(ElectricityStore.parse("")==null);
        check(ElectricityStore.parse("not json")==null);
        check(ElectricityStore.parse("{\"buildings\":[]}")==null);
        List<ElectricityMeter> partial=ElectricityStore.parse("{\"buildings\":[{\"name\":\"\"},{\"acId\":\"9\"}]}");
        check(partial==null);

        System.out.println("Electricity meter rules: "+checks+" checks passed");
    }
}

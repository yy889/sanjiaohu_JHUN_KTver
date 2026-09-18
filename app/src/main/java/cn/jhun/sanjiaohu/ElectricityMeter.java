package cn.jhun.sanjiaohu;

import java.util.*;

/**
 * 宿舍电表（roomverify）的编号模型。
 *
 * 移植自 eve_all 桌面版电费查询程序（package dianfei）的 Meter.kt，
 * 编号规律与楼栋对照表均为该程序**实测确认**的数据，此处逐条照抄。
 *
 * 表号结构：
 *
 *     大分类 - 编号 -- 楼层 - 房间
 *        3   -  3   --  4   - 407
 *
 * **重要**：照明和空调是两套**独立**的编号体系，同一栋楼在两边编号不同。
 *
 *   北区3舍：照明 id=3    空调 id=15
 *   北区1舍：照明 id=1    空调 id=13
 *   北区10舍：照明 id=10  空调 id=22
 *
 * 已实测的数据：
 *   灯光 3-3--4-407   大分类3(北校区照明) 照明id3(北区3舍) 楼层4 房间407
 *   空调 1-15--45-407 大分类1(学生空调)   空调id15(北区3舍) 号段45 房间407
 *
 * 因此 1-15--45-407 里的 15 是**北区3舍的空调编号**，不是「北区15舍」。
 *
 * 大分类 id：
 *   1 = 学生空调/南9-12北18-24空调和照明/商住区
 *   2 = 南校区照明
 *   3 = 北校区照明
 *
 * 注意中间是**两个连字符** `--`，写成单个会返回 FAIL。
 */
final class ElectricityMeter {
    /** 宿舍楼名称，用于界面显示与跨表关联，如 "北区3舍" */
    final String name;
    /** 照明编号（大分类 3 下使用）。null = 该楼无照明表 */
    final String lightId;
    /** 空调编号（大分类 1 下使用）。null = 该楼无空调表 */
    final String acId;
    /** 空调号段：楼层 -> 第三个数字。每栋楼不同，未配置的楼层界面会提示「未配置」。 */
    final Map<Integer,String> acSeg;

    ElectricityMeter(String name,String lightId,String acId,Map<Integer,String> acSeg){
        this.name=name;this.lightId=lightId;this.acId=acId;this.acSeg=acSeg==null?Collections.<Integer,String>emptyMap():acSeg;
    }

    /** 灯光表号；无照明编号返回 null。实测所有楼的灯光第三段都等于楼层本身。 */
    String lightRoomVerify(int floor,String room){
        if(lightId==null)return null;
        return "3-"+lightId+"--"+floor+"-"+floor+room;
    }

    /** 空调表号；无空调编号或该楼层号段未配置时返回 null。 */
    String acRoomVerify(int floor,String room){
        if(acId==null)return null;
        String seg=acSeg.get(floor);
        if(seg==null)return null;
        return "1-"+acId+"--"+seg+"-"+floor+room;
    }

    /** 该楼支持的类型描述。 */
    String supportText(){
        boolean hasLight=lightId!=null,hasAc=acId!=null;
        if(hasLight&&!acSeg.isEmpty())return "照明 + 空调";
        if(hasLight&&hasAc)return "照明 + 空调(号段待填)";
        if(hasLight)return "仅照明";
        if(hasAc)return "仅空调";
        return "未配置";
    }

    static final Map<String,String> CATEGORIES;
    static {
        Map<String,String> c=new LinkedHashMap<String,String>();
        c.put("1","学生空调/南9-12北18-24空调和照明/商住区");
        c.put("2","南校区照明");
        c.put("3","北校区照明");
        CATEGORIES=Collections.unmodifiableMap(c);
    }

    /**
     * 宿舍楼对照表。
     *
     * 左侧照明 id 来自「第二个数字」接口，右侧空调 id 来自「空调」接口，
     * 按**楼名**配对（两边编号体系不同）。
     *
     * acSeg 只有实测确认的北区3舍填全（42~47），其余楼的号段需要在 meters.json 里补。
     */
    static List<ElectricityMeter> defaultBuildings(){
        // 北区3舍的空调号段（实测 42=1层 … 47=6层）
        Map<Integer,String> north3=new LinkedHashMap<Integer,String>();
        north3.put(1,"42");north3.put(2,"43");north3.put(3,"44");
        north3.put(4,"45");north3.put(5,"46");north3.put(6,"47");

        List<ElectricityMeter> list=new ArrayList<ElectricityMeter>();
        // 北区（照明 id 齐全）
        list.add(new ElectricityMeter("北区1舍","1","13",null));
        list.add(new ElectricityMeter("北区2舍","2","14",null));
        list.add(new ElectricityMeter("北区3舍","3","15",north3));
        list.add(new ElectricityMeter("北区4舍","4","16",null));
        list.add(new ElectricityMeter("北区5舍","5","17",null));
        list.add(new ElectricityMeter("北区6舍","6","18",null));
        list.add(new ElectricityMeter("北区7舍","7","19",null));
        list.add(new ElectricityMeter("北区8舍","8","20",null));
        list.add(new ElectricityMeter("北区9舍","9","21",null));
        list.add(new ElectricityMeter("北区10舍","10","22",null));
        list.add(new ElectricityMeter("北区11舍","11","23",null));
        list.add(new ElectricityMeter("北区12舍","12","24",null));
        list.add(new ElectricityMeter("北区13舍","13","25",null));
        list.add(new ElectricityMeter("北区14舍","14","26",null));
        list.add(new ElectricityMeter("北区15A舍","15A","27",null));
        list.add(new ElectricityMeter("北区15B舍","15B","28",null));
        list.add(new ElectricityMeter("北区16舍","16","29",null));
        list.add(new ElectricityMeter("北区17舍","17","30",null));
        list.add(new ElectricityMeter("北区18舍","18","31",null));
        // 以下来自空调列表，照明列表里没有；照明 id 待补
        list.add(new ElectricityMeter("北区19舍",null,"32",null));
        list.add(new ElectricityMeter("北区20舍",null,"33",null));
        list.add(new ElectricityMeter("北区21舍",null,"35",null));
        list.add(new ElectricityMeter("北区22舍",null,"37",null));
        list.add(new ElectricityMeter("北区23舍",null,"36",null));
        list.add(new ElectricityMeter("北区24舍",null,"34",null));
        // 食堂公寓
        list.add(new ElectricityMeter("食堂公寓照明","19",null,null));
        list.add(new ElectricityMeter("食堂公寓空调","20",null,null));
        // 南区：只有空调列表给了编号，照明 id 待补
        list.add(new ElectricityMeter("南区1舍",null,"1",null));
        list.add(new ElectricityMeter("南区2舍",null,"2",null));
        list.add(new ElectricityMeter("南区3舍",null,"3",null));
        list.add(new ElectricityMeter("南区4舍",null,"4",null));
        list.add(new ElectricityMeter("南区5舍",null,"5",null));
        list.add(new ElectricityMeter("南区6舍",null,"6",null));
        list.add(new ElectricityMeter("南区7舍",null,"7",null));
        list.add(new ElectricityMeter("南区8舍",null,"8",null));
        list.add(new ElectricityMeter("南区9舍",null,"9",null));
        list.add(new ElectricityMeter("南区10舍",null,"10",null));
        list.add(new ElectricityMeter("南区11舍",null,"11",null));
        list.add(new ElectricityMeter("南区12舍",null,"12",null));
        return list;
    }
}

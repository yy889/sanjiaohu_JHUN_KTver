package cn.jhun.sanjiaohu;

import android.content.Context;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * 号段表（宿舍楼 ↔ 照明/空调编号）的本地存储。
 *
 * 移植自 eve_all 的 MeterStore.kt。文件格式与该桌面程序**完全一致**，
 * 因此两边的 meters.json 可以互相拷贝使用。
 *
 * 文件位置：App 私有目录 meters.json（不是 assets —— 用户可以改）。
 *
 * 格式：
 * {
 *   "buildings": [
 *     {"name":"北区3舍","lightId":"3","acId":"15","acSeg":{"1":"42","4":"45"}},
 *     ...
 *   ]
 * }
 *
 * acSeg 是「楼层 -> 该楼空调第三段」。每栋楼不同，未填的楼层界面会提示未配置。
 * lightId/acId 为 null 表示该楼没有对应的表。
 *
 * 与 eve_all 一样的取舍：这张表是**数据**（改一次能用很久、要能手工编辑与分享），
 * 所以单独成文件，不和运行时设置混在一起。
 */
final class ElectricityStore {
    private final File file;

    ElectricityStore(Context context){this.file=new File(context.getFilesDir(),"meters.json");}

    File file(){return file;}

    /** 读取号段表；文件不存在或损坏时回落到内置默认值并写出一份。 */
    List<ElectricityMeter> load(){
        if(!file.exists()||file.length()==0L){
            List<ElectricityMeter> defaults=ElectricityMeter.defaultBuildings();
            save(defaults);
            return defaults;
        }
        try{
            List<ElectricityMeter> parsed=parse(readText(file));
            if(parsed!=null&&!parsed.isEmpty())return parsed;
        }catch(Exception ignored){}
        return ElectricityMeter.defaultBuildings();
    }

    boolean save(List<ElectricityMeter> list){
        try{writeText(file,toJson(list));return true;}
        catch(Exception e){return false;}
    }

    // ---------------- 手写 JSON（不引第三方库，与 eve_all 一致） ----------------

    static String toJson(List<ElectricityMeter> list){
        StringBuilder sb=new StringBuilder();
        sb.append("{\n");
        sb.append("  \"_说明\": \"宿舍楼号段表。lightId=照明编号, acId=空调编号, 两者编号体系不同。acSeg 是楼层->空调第三段。\",\n");
        sb.append("  \"_示例\": \"北区3舍 4楼空调 = 1-15--45-407, 则 acId=15, acSeg={\\\"4\\\":\\\"45\\\"}\",\n");
        sb.append("  \"buildings\": [\n");
        for(int i=0;i<list.size();i++){
            ElectricityMeter m=list.get(i);
            sb.append("    {");
            sb.append("\"name\": ").append(quote(m.name)).append(", ");
            sb.append("\"lightId\": ").append(m.lightId==null?"null":quote(m.lightId)).append(", ");
            sb.append("\"acId\": ").append(m.acId==null?"null":quote(m.acId)).append(", ");
            sb.append("\"acSeg\": {");
            List<Integer> floors=new ArrayList<Integer>(m.acSeg.keySet());
            Collections.sort(floors);
            boolean first=true;
            for(Integer floor:floors){
                if(!first)sb.append(", ");
                first=false;
                sb.append(quote(String.valueOf(floor))).append(": ").append(quote(m.acSeg.get(floor)));
            }
            sb.append("}}");
            if(i!=list.size()-1)sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** 解析号段表；无法解析返回 null。 */
    static List<ElectricityMeter> parse(String text){
        Matcher root=Pattern.compile("\"buildings\"\\s*:\\s*\\[(.*)]",Pattern.DOTALL).matcher(text);
        if(!root.find())return null;

        // 按顶层 {} 切对象。两个易错点（eve_all 里踩过）：
        //  1) 嵌套大括号要保留，否则 acSeg 匹配不到
        //  2) 每个新对象必须清空缓冲，否则内容会累积
        List<String> objects=new ArrayList<String>();
        int depth=0;
        StringBuilder current=new StringBuilder();
        for(int i=0;i<root.group(1).length();i++){
            char ch=root.group(1).charAt(i);
            if(ch=='{'){depth++;if(depth==1)current.setLength(0);else current.append(ch);}
            else if(ch=='}'){depth--;if(depth==0)objects.add(current.toString());else current.append(ch);}
            else if(depth>=1)current.append(ch);
        }

        List<ElectricityMeter> list=new ArrayList<ElectricityMeter>();
        for(String object:objects){
            String name=string(object,"name");
            if(name==null||name.trim().isEmpty())continue;
            Map<Integer,String> seg=new LinkedHashMap<Integer,String>();
            Matcher body=Pattern.compile("\"acSeg\"\\s*:\\s*\\{([^}]*)\\}").matcher(object);
            String segText=body.find()?body.group(1):"";
            Matcher pair=Pattern.compile("\"(\\d+)\"\\s*:\\s*\"([^\"]+)\"").matcher(segText);
            while(pair.find()){
                try{seg.put(Integer.valueOf(pair.group(1)),pair.group(2));}catch(NumberFormatException ignored){}
            }
            list.add(new ElectricityMeter(name,
                identifier(string(object,"lightId")),
                identifier(string(object,"acId")),
                seg));
        }
        return list.isEmpty()?null:list;
    }

    /** "null" / 空串 视作没有该表（eve_all 的 meters.json 里存在字符串 "null" 的可能）。 */
    private static String identifier(String value){
        if(value==null)return null;
        String trimmed=value.trim();
        if(trimmed.isEmpty()||"null".equals(trimmed))return null;
        return trimmed;
    }

    /** 取 "key": "value" 里的 value（不匹配 null）。 */
    private static String string(String text,String key){
        Matcher m=Pattern.compile("\""+Pattern.quote(key)+"\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(text);
        if(!m.find())return null;
        return m.group(1).replace("\\n","\n").replace("\\t","\t").replace("\\\"","\"").replace("\\\\","\\");
    }

    private static String quote(String value){
        String escaped=value.replace("\\","\\\\").replace("\"","\\\"")
            .replace("\n","\\n").replace("\r","").replace("\t","\\t");
        return "\""+escaped+"\"";
    }

    private static String readText(File target)throws IOException{
        InputStream in=new FileInputStream(target);
        try{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buffer=new byte[8192];int read;
            while((read=in.read(buffer))!=-1)out.write(buffer,0,read);
            return out.toString("UTF-8");
        }finally{in.close();}
    }

    private static void writeText(File target,String text)throws IOException{
        OutputStream out=new FileOutputStream(target);
        try{out.write(text.getBytes("UTF-8"));}finally{out.close();}
    }
}

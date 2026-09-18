package cn.jhun.sanjiaohu;

import android.content.Context;
import java.io.File;

/**
 * 号段表的落盘检查：首次运行写出默认表、改过的表能读回、损坏文件回落到默认值。
 *
 * 用 tests/context-stubs 里的 Context 替身（真实 Android Context 不可用），
 * 文件落在临时目录里。
 */
public final class ElectricityStoreTest {
    static int checks;
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Case "+checks);}

    public static void main(String[] args)throws Exception{
        File dir=new File(System.getProperty("java.io.tmpdir"),"electricity-store-"+System.nanoTime());
        check(dir.mkdirs());
        try{
            ElectricityStore store=new ElectricityStore(new Context(dir));
            check(!store.file().exists());

            // 首次读取：写出默认表并返回它
            java.util.List<ElectricityMeter> first=store.load();
            check(store.file().exists());
            check(first.size()>0);
            check(store.file().length()>0);

            // 再读一次应得到同样规模（走解析路径，而不是默认值路径）
            java.util.List<ElectricityMeter> second=store.load();
            check(second.size()==first.size());

            // 改名后落盘能读回（证明 save -> load 往返保真）
            java.util.List<ElectricityMeter> edited=new java.util.ArrayList<ElectricityMeter>();
            java.util.Map<Integer,String> seg=new java.util.LinkedHashMap<Integer,String>();
            seg.put(4,"48");
            edited.add(new ElectricityMeter("测试楼","9","99",seg));
            check(store.save(edited));
            java.util.List<ElectricityMeter> reloaded=store.load();
            check(reloaded.size()==1);
            check(reloaded.get(0).name.equals("测试楼"));
            check(reloaded.get(0).lightId.equals("9"));
            check(reloaded.get(0).acId.equals("99"));
            check("1-99--48-407".equals(reloaded.get(0).acRoomVerify(4,"07")));

            // 损坏文件：回落到内置默认表，不抛异常
            java.io.FileOutputStream out=new java.io.FileOutputStream(store.file());
            out.write("{ this is not json".getBytes("UTF-8"));
            out.close();
            java.util.List<ElectricityMeter> recovered=store.load();
            check(recovered.size()==ElectricityMeter.defaultBuildings().size());

            // 空文件同样回落
            new java.io.FileOutputStream(store.file()).close();
            check(store.load().size()==ElectricityMeter.defaultBuildings().size());

            System.out.println("Electricity store: "+checks+" checks passed");
        }finally{
            File[] files=dir.listFiles();
            if(files!=null)for(File f:files)f.delete();
            dir.delete();
        }
    }
}

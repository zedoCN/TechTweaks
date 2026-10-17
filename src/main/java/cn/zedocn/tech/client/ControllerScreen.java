package cn.zedocn.tech.client;

import cn.zedocn.tech.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ControllerScreen extends AbstractContainerScreen<ControllerMenu> {
    private final Button[] buttons = new Button[8];
    public ControllerScreen(ControllerMenu menu,Inventory inv,Component title) { super(menu,inv,title,176,222); inventoryLabelY=126; }
    @Override public void init() {
        super.init();
        for(int i=0;i<8;i++) {
            final int id=i;
            buttons[i]=addRenderableWidget(Button.builder(Component.empty(), b -> { if(minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id); }).bounds(leftPos+8+i%2*81,topPos+36+i/2*15,79,14).build());
        }
        updateButtons();
    }
    private void updateButtons() {
        int kind=menu.data.get(0);
        String[] labels={menu.data.get(1)==1?"启用：开":"启用：关","减少","增加","时间："+new String[]{"自然","白天","夜晚"}[menu.data.get(4)],"天气："+new String[]{"自然","晴天","雨天","雷雨"}[menu.data.get(5)],"开始一轮",menu.data.get(6)==1?"试炼：不祥":"试炼：普通",menu.data.get(7)==1?"红石控制：开":"红石控制：关"};
        for(int i=0;i<8;i++) {
            buttons[i].setMessage(Component.literal(labels[i]));
            buttons[i].visible = i==0 || i==7 || (kind==0 && (i==1 || i==2 || i==5 || i==6)) || (kind==2 && (i==3 || i==4)) || (kind==3 && (i==1 || i==2));
        }
        if (kind==0) buttons[0].setMessage(Component.literal(menu.data.get(1)==1?"自动：开":"自动：关"));
        int[][] positions={{8,36},{8,51},{89,51},{8,51},{89,51},{89,66},{8,66},{89,36}};
        for(int i=0;i<8;i++) { buttons[i].setX(leftPos+positions[i][0]); buttons[i].setY(topPos+positions[i][1]); }

    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float a) { updateButtons(); super.extractRenderState(g,x,y,a); }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float a) {
        super.extractBackground(g,x,y,a);
        g.fill(leftPos,topPos,leftPos+176,topPos+222,0xFFCED2D8);
        for(var slot:menu.slots) {
            int sx=leftPos+slot.x,sy=topPos+slot.y;
            g.fill(sx-1,sy-1,sx+17,sy+17,0xFF555960); g.fill(sx,sy,sx+16,sy+16,0xFF9EA3AA);
        }
        g.outline(leftPos+7,topPos+99,18,18,0xFFCA8B28);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y) {
        super.extractLabels(g,x,y);
        int energy=(menu.data.get(8)&65535)|(menu.data.get(9)&65535)<<16;
        String detail=switch(menu.data.get(0)) { case 0 -> "下轮人数："+menu.data.get(2)+(menu.data.get(11)>0?" 活怪："+menu.data.get(12):""); case 3 -> "区域半径："+menu.data.get(3)+" 格"; default -> "红石／物品接口自动运行"; };
        g.text(font,detail,8,18,0xFF30343A,false);
        g.text(font,"储能："+energy+" FE",8,27,0xFF30343A,false);
        String[] status={"就绪","正面没有目标","电力不足","轮次进行中","缺少瓶子／正确钥匙","已暂停","维度／刷怪规则不允许","输出已满","其他环境控制器占用","正在刷怪","准备自动启动","刷怪受阻：检查生成条件","等待清理本轮怪物","正在结算奖励","等待冷却重置","刷怪笼未配置生物"};
        g.text(font,status[Math.clamp(menu.data.get(10),0,status.length-1)],8,89,0xFF30343A,false);
        g.text(font,"输入",8,119,0xFF805410,false); g.text(font,"输出 → 漏斗／管道",38,119,0xFF30343A,false);
    }
}

package cn.jhun.sanjiaohu;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Shared, stateless view factory.
 *
 * These helpers were previously copied into seven classes (MainActivity, AboutActivity,
 * IdentityActivity, LoginActivity, MoreMenu, CampusMapActivity, AcademicCalendarActivity).
 * Each copy is numerically identical, so this class is a transcription of the existing
 * behaviour rather than a redesign.
 *
 * Deliberately stateless: every helper takes the Context it builds against. Theme-dependent
 * helpers (label/button/themedButton/panel) stay in MainActivity because they read instance
 * theme fields; they are not part of this extraction.
 *
 * dp() keeps the original "multiply then add 0.5f then truncate" rounding. Do not replace it
 * with Math.round(): that rounds negative values in the opposite direction and would shift
 * layouts.
 */
final class Ui {
    private Ui(){}

    static int dp(Context c,float value){return (int)(c.getResources().getDisplayMetrics().density*value+.5f);}

    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}

    static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setGravity(Gravity.CENTER_VERTICAL);return v;}

    static LinearLayout.LayoutParams weighted(Context c){return new LinearLayout.LayoutParams(0,dp(c,44),1);}

    /** Vertical spacer: a 1px-wide View of the requested height. */
    static void space(Context c,LinearLayout parent,int size){parent.addView(new View(c),new LinearLayout.LayoutParams(1,dp(c,size)));}

    static void place(FrameLayout parent,View v,int x,int y,int w,int h){
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(w,h);
        lp.leftMargin=x;lp.topMargin=y;parent.addView(v,lp);
    }

    static GradientDrawable shape(Context c,int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;
    }
}

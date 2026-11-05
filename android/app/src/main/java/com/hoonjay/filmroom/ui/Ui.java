package com.hoonjay.filmroom.ui;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;

public final class Ui {
  public static final int HEADER_CONTROL_DP=32;
  public static final int BG = 0xff19191b,
      PANEL = 0xff252426,
      TEXT = 0xffeee9df,
      MUTED = 0xffaaa39a,
      ACCENT = 0xfff5a36b;

  public static int dp(Context c, float n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  public static TextView text(Context c, String t, int size, int color) {
    TextView v = new TextView(c);
    v.setText(t);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setPadding(0, dp(c, 5), 0, dp(c, 5));
    return v;
  }

  public static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setBackgroundColor(BG);
    l.setPadding(dp(c, 20), dp(c, 15), dp(c, 20), dp(c, 24));
    return l;
  }

  public static LinearLayout row(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setBaselineAligned(false);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private static GradientDrawable surface(Context c,int color,int stroke){
    GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,11));if(stroke!=0)d.setStroke(Math.max(1,dp(c,.7f)),stroke);return d;
  }
  private static Drawable buttonBackground(Context c,boolean outlined){
    StateListDrawable states=new StateListDrawable();
    states.addState(new int[]{-android.R.attr.state_enabled},surface(c,0x00000000,outlined?0xff2b292d:0));
    states.addState(new int[]{android.R.attr.state_selected},surface(c,0xff382a22,0xffc88d61));
    states.addState(new int[]{android.R.attr.state_activated},surface(c,0xff30261f,0xff8d654a));
    states.addState(new int[]{},surface(c,0x00000000,outlined?0xff3c383c:0));
    return new RippleDrawable(ColorStateList.valueOf(0x2ef5a36b),states,surface(c,Color.WHITE,0));
  }
  private static ColorStateList buttonInk(){return new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{android.R.attr.state_selected},{android.R.attr.state_activated},{}},new int[]{0xff625d60,ACCENT,ACCENT,TEXT});}
  private static String illustration(String text){
    switch(text){
      case "프리셋":case "프리셋 적용":case "프리셋 저장":return "preset";
      case "초기화":case "전체 커브 초기화":case "선택 RGB 커브 초기화":case "크기조정 초기화":return "reset";
      case "실행취소":return "undo";case "갤러리":case "전체 사진":return "gallery";case "선택":case "전체선택":case "전체":case "취소":case "선택 취소":return "selection";case "편집 복사":return "edit_copy";case "편집 붙여넣기":return "edit_paste";
      case "밝기":return "brightness";case "색상":return "color";case "효과":return "effects";case "앨범":return "gallery";
      case "크기조정":case "잘라내기":case "잘라내기 적용":return "crop";
      case "현상":case "현상하기":case "현상해서 저장":return "develop";
      case "90° 회전":return "rotate";case "좌우 반전":return "mirror";case "작업실":return "curves";
      default:return null;
    }
  }
  private static void illustrated(Context c,Button b,String label,boolean vertical,int size){
    String key=illustration(label);if(key==null)return;int id=c.getResources().getIdentifier("ui_"+key,"drawable",c.getPackageName());
    if(id==0)throw new IllegalStateException("Missing UI illustration: "+key);
    Drawable icon=c.getDrawable(id).mutate();icon.setBounds(0,0,dp(c,size),dp(c,size));b.setCompoundDrawablePadding(dp(c,2));
    if(vertical)b.setCompoundDrawables(null,icon,null,null);else b.setCompoundDrawables(icon,null,null,null);
  }
  public static Button button(Context c,String text,boolean primary){
    Button b=new IllustratedButton(c);b.setText(text);b.setTextColor(buttonInk());b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setIncludeFontPadding(false);
    b.setGravity(Gravity.CENTER);b.setStateListAnimator(null);b.setElevation(0);b.setActivated(primary);b.setBackgroundTintList(null);b.setCompoundDrawableTintList(null);b.setBackground(buttonBackground(c,true));b.setPadding(dp(c,12),dp(c,6),dp(c,12),dp(c,6));b.setMinHeight(dp(c,44));
    illustrated(c,b,text,false,20);return b;
  }
  public static Button action(Context c,String label,boolean primary){
    Button b=button(c,label,primary);b.setSingleLine(true);b.setHorizontallyScrolling(false);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(c,8),0,dp(c,8),0);return b;
  }
  private static final class IllustratedButton extends Button{
    IllustratedButton(Context c){super(c);}
    public void scrollTo(int x,int y){super.scrollTo(0,0);}
    private int edgeLeft,edgeRight,edgeTop,edgeBottom;
    private boolean balancing;
    public void setPadding(int left,int top,int right,int bottom){if(!balancing){edgeLeft=left;edgeTop=top;edgeRight=right;edgeBottom=bottom;}super.setPadding(left,top,right,bottom);}
    protected void onLayout(boolean changed,int left,int top,int right,int bottom){
      super.onLayout(changed,left,top,right,bottom);
      Drawable[] icons=getCompoundDrawables();Drawable icon=icons[0];int extra=0;
      if(icon!=null&&icons[1]==null&&icons[2]==null&&icons[3]==null&&getMaxLines()==1){
        float gap=getCompoundDrawablePadding(),available=Math.max(0,getWidth()-edgeLeft-edgeRight-icon.getBounds().width()-gap);
        CharSequence label=android.text.TextUtils.ellipsize(getText(),getPaint(),available,android.text.TextUtils.TruncateAt.END);
        float content=icon.getBounds().width()+gap+(float)Math.ceil(getPaint().measureText(label.toString()))+dp(getContext(),2);
        extra=Math.max(0,(int)Math.floor((getWidth()-edgeLeft-edgeRight-content)/2));
      }
      int targetLeft=edgeLeft+extra,targetRight=edgeRight+extra;
      if(getPaddingLeft()!=targetLeft||getPaddingRight()!=targetRight){balancing=true;try{super.setPadding(targetLeft,edgeTop,targetRight,edgeBottom);}finally{balancing=false;}}
    }
    protected void drawableStateChanged(){super.drawableStateChanged();for(Drawable icon:getCompoundDrawables())if(icon!=null)icon.setAlpha(isEnabled()?255:65);}
  }
  public static Button photoAction(Context c,String label,boolean primary){Button button=action(c,label,primary);button.setTextSize(10);button.setPadding(dp(c,4),0,dp(c,4),0);illustrated(c,button,label,false,15);button.setCompoundDrawablePadding(dp(c,2));button.setAutoSizeTextTypeUniformWithConfiguration(8,10,1,android.util.TypedValue.COMPLEX_UNIT_SP);return button;}
  public static float photoWeight(String label){switch(label){case "편집 붙여넣기":return 1.7f;case "프리셋 적용":return 1.35f;case "실행취소":return 1.05f;case "현상":return .8f;default:return 1.25f;}}
  public static Button toolButton(Context c,String label){
    Button b=action(c,label,false);b.setBackground(buttonBackground(c,false));b.setTextSize(9.5f);b.setPadding(dp(c,2),dp(c,3),dp(c,2),dp(c,3));illustrated(c,b,label,true,22);return b;
  }
  public static LinearLayout compactHeader(Context c,String title,Runnable back){
    LinearLayout row=row(c);row.setPadding(0,dp(c,2),0,dp(c,2));
    Button close=action(c,"‹",false);close.setTextSize(22);close.setBackgroundColor(Color.TRANSPARENT);close.setContentDescription("뒤로");close.setOnClickListener(v->back.run());row.addView(close,new LinearLayout.LayoutParams(dp(c,36),dp(c,HEADER_CONTROL_DP)));
    TextView text=text(c,title,15,TEXT);text.setPadding(dp(c,6),0,0,0);text.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);text.setIncludeFontPadding(false);text.setSingleLine();text.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(text,new LinearLayout.LayoutParams(0,dp(c,HEADER_CONTROL_DP),1));return row;
  }
  public static void modal(Activity a,Dialog dialog,View content){
    dialog.setContentView(content);dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(BG));
      Rect bounds=a.getWindowManager().getCurrentWindowMetrics().getBounds();w.setLayout(Math.min(dp(a,380),bounds.width()-dp(a,24)),WindowManager.LayoutParams.WRAP_CONTENT);}
  }

  public static View divider(Context c) {
    View v = new View(c);
    v.setBackgroundColor(0xff3d3835);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(c, 1));
    lp.setMargins(0, dp(c, 15), 0, dp(c, 15));
    v.setLayoutParams(lp);
    return v;
  }

  public static ScrollView scroll(Context c, View content) {
    ScrollView s = new ScrollView(c);
    s.setFillViewport(true);
    s.setBackgroundColor(BG);
    s.addView(content);
    return s;
  }

  public static void title(LinearLayout col, String t, Runnable close) {
    Context c = col.getContext();
    LinearLayout row = row(c);
    Button back = button(c, "‹", false);
    back.setContentDescription("뒤로");
    back.setOnClickListener(v -> close.run());
    row.addView(back, new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
    TextView label = text(c, t, 19, TEXT);
    label.setPadding(dp(c, 15), 0, 0, 0);
    row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    col.addView(row);
    col.addView(divider(c));
  }

  public static void dialog(Activity activity, String title, String message) {
    AlertDialog d =
        new AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("확인", null)
            .create();
    d.setOnShowListener(
        x -> {
          d.getWindow().setBackgroundDrawable(new ColorDrawable(PANEL));
          d.getButton(-1).setTextColor(ACCENT);
          TextView m = d.findViewById(android.R.id.message);
          if (m != null) {
            m.setTextColor(TEXT);
            m.setLineSpacing(dp(activity, 4), 1);
          }
        });
    d.show();
  }

  private static GradientDrawable rounded(Context c, int color, float radius) {
    GradientDrawable background=new GradientDrawable();
    background.setColor(color);
    background.setCornerRadius(dp(c,radius));
    return background;
  }

  /** App-owned guide surface: no platform title/footer strips, scrollable at either orientation. */
  public static void guide(Activity activity, String title, String message, Consumer<Boolean> save) {
    Dialog dialog=new Dialog(activity);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout card=new LinearLayout(activity);
    card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable surface=rounded(activity,BG,18);
    surface.setStroke(dp(activity,1),0xff3d3835);
    card.setBackground(surface);
    card.setClipToOutline(true);

    LinearLayout heading=new LinearLayout(activity);
    heading.setOrientation(LinearLayout.VERTICAL);
    heading.setPadding(dp(activity,22),dp(activity,17),dp(activity,22),dp(activity,12));
    heading.addView(text(activity,"촬영 가이드",11,ACCENT));
    TextView name=text(activity,title,22,TEXT);
    name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
    heading.addView(name);
    card.addView(heading);

    LinearLayout content=new LinearLayout(activity);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(activity,20),0,dp(activity,20),dp(activity,10));
    for(String block : message.split("\\n\\n")) {
      boolean parameter=block.startsWith("셔터스피드 ·") || block.startsWith("ISO ·")
          || block.startsWith("타이머 ·") || block.startsWith("플래시 ·");
      if(parameter) {
        int newline=block.indexOf('\n');
        LinearLayout section=new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(activity,14),dp(activity,9),dp(activity,14),dp(activity,11));
        section.setBackground(rounded(activity,PANEL,11));
        TextView label=text(activity,newline<0?block:block.substring(0,newline),14,ACCENT);
        label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        section.addView(label);
        if(newline>=0) {
          TextView explanation=text(activity,block.substring(newline+1),13,TEXT);
          explanation.setLineSpacing(dp(activity,3),1);
          section.addView(explanation);
        }
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);
        space.setMargins(0,dp(activity,9),0,0);
        content.addView(section,space);
      } else {
        TextView paragraph=text(activity,block,13,MUTED);
        paragraph.setLineSpacing(dp(activity,4),1);
        paragraph.setPadding(0,dp(activity,9),0,dp(activity,5));
        content.addView(paragraph);
      }
    }
    CheckBox hide=new CheckBox(activity);
    hide.setText("이 모드 안내 다시 보지 않기");
    hide.setTextColor(MUTED);
    hide.setTextSize(12);
    hide.setButtonTintList(new ColorStateList(
        new int[][] {new int[]{android.R.attr.state_checked},new int[]{}},
        new int[]{ACCENT,MUTED}));
    hide.setMinHeight(dp(activity,48));
    content.addView(hide);
    ScrollView scrolling=new ScrollView(activity);
    scrolling.setVerticalScrollBarEnabled(false);
    scrolling.setClipToPadding(false);
    scrolling.addView(content);
    card.addView(scrolling,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout footer=new LinearLayout(activity);
    footer.setPadding(dp(activity,20),dp(activity,10),dp(activity,20),dp(activity,18));
    Button confirm=button(activity,"확인",true);
    confirm.setOnClickListener(v -> {save.accept(hide.isChecked()); dialog.dismiss();});
    footer.addView(confirm,new LinearLayout.LayoutParams(-1,dp(activity,48)));
    card.addView(footer);
    dialog.setContentView(card);
    dialog.show();
    Runnable resize=() -> {
    Window window=dialog.getWindow();
    if(window!=null) {
      window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      window.setDimAmount(.7f);
      android.graphics.Rect bounds=activity.getWindowManager().getCurrentWindowMetrics().getBounds();
      android.graphics.Insets insets=activity.getWindowManager().getCurrentWindowMetrics()
          .getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
      int width=Math.min(dp(activity,520),bounds.width()-insets.left-insets.right-dp(activity,28));
      int height=Math.round((bounds.height()-insets.top-insets.bottom)*.88f);
      window.setLayout(width,height);
      window.setGravity(Gravity.CENTER);
    }
    };
    View host=activity.getWindow().getDecorView();
    View.OnLayoutChangeListener orientation=(v,l,t,r,b,ol,ot,or,ob) -> {
      if(r-l!=or-ol || b-t!=ob-ot) resize.run();
    };
    host.addOnLayoutChangeListener(orientation);
    dialog.setOnDismissListener(d -> host.removeOnLayoutChangeListener(orientation));
    resize.run();
  }
}

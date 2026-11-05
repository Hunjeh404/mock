package com.hoonjay.filmroom.guides;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.hoonjay.filmroom.ui.Ui;
import java.util.*;
import org.json.*;

/** Two-column, downward-opening native picker. Category + keyword recommendations are combined. */
public final class GuidePicker extends PopupWindow {
  public interface Listener {
    void preview(GuideCatalog.Selection s);

    void apply(GuideCatalog.Selection s);
  }

  private final Activity activity;
  private final GuideCatalog catalog;
  private final GuideRenderer renderer;
  private final Listener listener;
  private final GuideCatalog.Selection original;
  private GuideCatalog.Selection draft;
  private boolean accepted, all;
  private String query = "";
  private LinearLayout categoryList, photoList, footerOptions;
  private TextView selectedText, resultLabel;
  private EditText search;
  private Button recommended, allButton;
  private boolean rebuilding;

  public GuidePicker(Activity a, GuideCatalog c, GuideCatalog.Selection selected, Listener l) {
    super(a);
    activity = a;
    catalog = c;
    renderer = new GuideRenderer(c);
    listener = l;
    original = selected.copy();
    draft = selected.copy();
    setFocusable(true);
    setOutsideTouchable(true);
    setElevation(Ui.dp(a, 12));
    setInputMethodMode(INPUT_METHOD_NEEDED);
    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Ui.BG);
    bg.setCornerRadius(Ui.dp(a, 12));
    bg.setStroke(Ui.dp(a, 1), 0xff665044);
    setBackgroundDrawable(bg);
    build();
    setOnDismissListener(
        () -> {
          if (!accepted) listener.preview(original.copy());
        });
  }

  public void show(View parent, int top) {
    Rect b = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
    setWidth(b.width() - Ui.dp(activity, 12));
    setHeight(
        Math.min(
            Ui.dp(activity, 620),
            Math.max(Ui.dp(activity, 270), b.height() - top - Ui.dp(activity, 18))));
    showAtLocation(parent, Gravity.TOP | Gravity.LEFT, Ui.dp(activity, 6), top);
  }

  private int dp(int n) {
    return Ui.dp(activity, n);
  }

  private TextView text(String s, int size, int color) {
    return Ui.text(activity, s, size, color);
  }

  private void build() {
    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(1);
    root.setPadding(dp(10), dp(10), dp(10), dp(8));
    LinearLayout head = Ui.row(activity);
    TextView title = text("촬영 가이드", 15, Ui.TEXT);
    head.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1));
    Button cancel = Ui.action(activity, "닫기", false);
    head.addView(cancel, new LinearLayout.LayoutParams(dp(52), dp(32)));
    cancel.setOnClickListener(v -> dismiss());
    root.addView(head);
    search = new EditText(activity);
    search.setSingleLine();
    search.setTextSize(13);
    search.setTextColor(Ui.TEXT);
    search.setHintTextColor(Ui.MUTED);
    search.setHint("키워드 · 업무협약, AI, 봉사");
    search.setPadding(dp(10), 0, dp(10), 0);
    GradientDrawable field = new GradientDrawable();
    field.setColor(Ui.PANEL);
    field.setCornerRadius(dp(7));
    search.setBackground(field);
    root.addView(search, new LinearLayout.LayoutParams(-1, dp(42)));
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            query = s.toString();
            refresh();
          }

          public void afterTextChanged(Editable e) {}
        });
    LinearLayout labels = Ui.row(activity);
    TextView catTitle = text("보도자료 분류 · 복수 선택", 10, Ui.MUTED);
    labels.addView(catTitle, new LinearLayout.LayoutParams(0, dp(37), .42f));
    recommended = Ui.action(activity, "추천", true);
    allButton = Ui.action(activity, "전체", false);
    labels.addView(recommended, new LinearLayout.LayoutParams(0, dp(30), .29f));
    labels.addView(allButton, new LinearLayout.LayoutParams(0, dp(30), .29f));
    recommended.setOnClickListener(
        v -> {
          all = false;
          refresh();
        });
    allButton.setOnClickListener(
        v -> {
          all = true;
          refresh();
        });
    root.addView(labels);
    LinearLayout columns = Ui.row(activity);
    columns.setGravity(Gravity.TOP);
    categoryList = new LinearLayout(activity);
    categoryList.setOrientation(1);
    photoList = new LinearLayout(activity);
    photoList.setOrientation(1);
    ScrollView left = new ScrollView(activity);
    left.setFillViewport(false);
    left.addView(categoryList);
    ScrollView right = new ScrollView(activity);
    right.addView(photoList);
    columns.addView(left, new LinearLayout.LayoutParams(0, -1, .42f));
    columns.addView(right, new LinearLayout.LayoutParams(0, -1, .58f));
    root.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));
    resultLabel = text("", 9, Ui.MUTED);
    root.addView(resultLabel);
    footerOptions = Ui.row(activity);
    root.addView(footerOptions, new LinearLayout.LayoutParams(-1, dp(38)));
    LinearLayout foot = Ui.row(activity);
    selectedText = text("", 11, Ui.TEXT);
    foot.addView(selectedText, new LinearLayout.LayoutParams(0, dp(46), 1));
    Button apply = Ui.action(activity, "가이드 적용", true);
    foot.addView(apply, new LinearLayout.LayoutParams(dp(98), dp(40)));
    apply.setOnClickListener(
        v -> {
          accepted = true;
          catalog.normalize(draft);
          listener.apply(draft.copy());
          dismiss();
        });
    root.addView(foot);
    setContentView(root);
    refresh();
  }

  private void refresh() {
    if (categoryList == null || rebuilding) return;
    rebuilding = true;
    categoryList.removeAllViews();
    photoList.removeAllViews();
    List<String> tokens = GuideCatalog.tokens(query);
    String group = "";
    int count = 0;
    for (JSONObject category : catalog.categories) {
      String id = category.optString("id");
      boolean selected = draft.categoryIds.contains(id);
      if (!tokens.isEmpty() && GuideCatalog.hits(category, tokens) == 0 && !selected) continue;
      String next = category.optString("group");
      if (!next.equals(group)) {
        TextView label = text(next, 9, Ui.MUTED);
        categoryList.addView(label);
        group = next;
      }
      CheckBox check = new CheckBox(activity);
      check.setText(category.optString("name"));
      check.setTextSize(10);
      check.setTextColor(selected ? Ui.ACCENT : Ui.TEXT);
      check.setButtonTintList(
          android.content.res.ColorStateList.valueOf(selected ? Ui.ACCENT : Ui.MUTED));
      check.setChecked(selected);
      check.setMinHeight(dp(43));
      check.setPadding(0, 0, dp(4), 0);
      categoryList.addView(check, new LinearLayout.LayoutParams(-1, -2));
      check.setOnCheckedChangeListener(
          (b, on) -> {
            if (on) draft.categoryIds.add(id);
            else draft.categoryIds.remove(id);
            all = false;
            refresh();
          });
      count++;
    }
    if (count == 0) categoryList.addView(text("일치하는 분류가 없습니다.", 10, Ui.MUTED));
    List<GuideCatalog.Match> rows = catalog.recommend(draft.categoryIds, query, all);
    for (GuideCatalog.Match m : rows) {
      JSONObject guide = m.guide;
      String id = guide.optString("id");
      LinearLayout cell = Ui.row(activity);
      cell.setPadding(dp(5), dp(4), dp(5), dp(4));
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(id.equals(draft.guideId) ? 0xff392b23 : Ui.PANEL);
      bg.setCornerRadius(dp(7));
      bg.setStroke(dp(1), id.equals(draft.guideId) ? Ui.ACCENT : 0xff393438);
      cell.setBackground(bg);
      GuideCatalog.Selection thumb = draft.copy();
      thumb.guideId = id;
      thumb.people = guide.optInt("defaultPeople");
      thumb.prop = "individual";
      thumb.visible = true;
      View mini =
          new View(activity) {
            protected void onDraw(Canvas c) {
              renderer.draw(c, new RectF(0, 0, getWidth(), getHeight()), thumb, 0, true);
            }
          };
      cell.addView(mini, new LinearLayout.LayoutParams(dp(53), dp(48)));
      LinearLayout words = new LinearLayout(activity);
      words.setOrientation(1);
      words.setPadding(dp(4), 0, 0, 0);
      words.addView(
          text((id.equals(draft.guideId) ? "✓ " : "") + guide.optString("name"), 11, Ui.TEXT));
      TextView why = text((m.primary ? "추천 · " : "") + m.reason, 8, Ui.MUTED);
      why.setMaxLines(2);
      words.addView(why);
      cell.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.setMargins(dp(4), 0, 0, dp(6));
      photoList.addView(cell, lp);
      cell.setContentDescription(guide.optString("name") + " · " + m.reason);
      cell.setOnClickListener(
          v -> {
            draft.guideId = id;
            catalog.normalize(draft);
            draft.visible = true;
            listener.preview(draft.copy());
            refresh();
          });
    }
    if (rows.isEmpty()) photoList.addView(text("일치하는 사진이 없습니다.\n전체에서 선택할 수 있습니다.", 11, Ui.MUTED));
    boolean keywordFound = false;
    for (JSONObject c : catalog.categories)
      if (GuideCatalog.hits(c, tokens) > 0) keywordFound = true;
    for (JSONObject g : catalog.guides) if (GuideCatalog.hits(g, tokens) > 0) keywordFound = true;
    resultLabel.setText(
        (!tokens.isEmpty() && !keywordFound ? "키워드 일치 없음 · " : "")
            + "선택 분류 "
            + draft.categoryIds.size()
            + "개 · 사진 "
            + rows.size()
            + "종");
    recommended.setActivated(!all);
    allButton.setActivated(all);
    selectedText.setText(catalog.guide(draft.guideId).optString("name"));
    footerOptions.removeAllViews();
    JSONObject selected = catalog.guide(draft.guideId);
    JSONArray people = selected.optJSONArray("peopleOptions");
    if (people.length() > 1) {
      TextView label = text("인원", 10, Ui.MUTED);
      footerOptions.addView(label);
      Spinner spinner = new Spinner(activity);
      List<String> names = new ArrayList<>();
      int at = 0;
      for (int i = 0; i < people.length(); i++) {
        names.add(people.optInt(i) + "명");
        if (people.optInt(i) == draft.people) at = i;
      }
      ArrayAdapter<String> adapter =
          new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, names);
      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
      spinner.setAdapter(adapter);
      spinner.setSelection(at);
      footerOptions.addView(spinner, new LinearLayout.LayoutParams(dp(80), dp(35)));
      spinner.setOnItemSelectedListener(
          new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) {}

            public void onItemSelected(AdapterView<?> p, View v, int i, long x) {
              int n = people.optInt(i);
              if (draft.people != n) {
                draft.people = n;
                listener.preview(draft.copy());
              }
            }
          });
    }
    if (draft.guideId.equals("held-prop")) {
      Button prop = Ui.action(activity, draft.prop.equals("shared") ? "가운데 하나" : "각자 들기", false);
      footerOptions.addView(prop, new LinearLayout.LayoutParams(dp(92), dp(32)));
      prop.setOnClickListener(
          v -> {
            draft.prop = draft.prop.equals("shared") ? "individual" : "shared";
            listener.preview(draft.copy());
            refresh();
          });
    }
    Button show = Ui.action(activity, draft.visible ? "가이드 켜짐" : "가이드 꺼짐", draft.visible);
    footerOptions.addView(show, new LinearLayout.LayoutParams(0, dp(32), 1));
    show.setOnClickListener(
        v -> {
          draft.visible = !draft.visible;
          listener.preview(draft.copy());
          refresh();
        });
    rebuilding = false;
  }
}

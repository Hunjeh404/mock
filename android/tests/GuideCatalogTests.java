import android.content.Context;
import com.hoonjay.filmroom.guides.GuideCatalog;
import java.util.*;
public final class GuideCatalogTests {
  static int checks;static void ok(boolean b,String n){checks++;if(!b)throw new AssertionError(n);}
  static boolean has(List<GuideCatalog.Match> rows,String id){for(GuideCatalog.Match m:rows)if(m.guide.optString("id").equals(id))return true;return false;}
  public static void main(String[] args)throws Exception{
    GuideCatalog c=new GuideCatalog(new Context(args[0]));
    ok(c.guides.size()==20&&c.categories.size()==20,"Catalog count");
    Set<String> selected=new LinkedHashSet<>(Arrays.asList("agreement","service-launch"));
    List<GuideCatalog.Match> mixed=c.recommend(selected,"AI 업무협약",false);
    ok(has(mixed,"demo")&&has(mixed,"held-prop")&&has(mixed,"handshake"),"Combined release and keyword candidates");
    Set<String> unique=new HashSet<>();for(GuideCatalog.Match m:mixed)ok(unique.add(m.guide.optString("id")),"No duplicate recommendation");
    ok(c.recommend(Collections.emptySet(),"unknownzzzxxyy",false).isEmpty(),"Unknown keyword produces no false result");
    ok(has(c.recommend(Collections.emptySet(),"MOU",false),"signing"),"English alias");
    ok(has(c.recommend(Collections.emptySet(),"기부",false),"handover"),"Donation delivery");
    ok(c.recommend(selected,"전혀없는키워드",true).size()==20,"All view remains available");
    ok(has(c.recommend(Collections.singleton("agreement"),"봉사",false),"activity"),"Keyword adds candidates without losing selected classification");
    GuideCatalog.Selection s=c.restore("{\"guideId\":\"held-prop\",\"people\":4,\"prop\":\"shared\",\"categoryIds\":[\"agreement\"]}");
    ok(s.people==4&&s.prop.equals("shared"),"Selection restore");ok(c.drawing(s,true).length()>0&&c.drawing(s,false).length()>0,"Landscape and portrait layout");
    GuideCatalog.Selection copy=s.copy();copy.categoryIds.add("event");copy.people=1;ok(s.people==4&&!s.categoryIds.contains("event"),"Draft copy cannot mutate accepted selection");
    s.guideId="unknown";s.people=999;s.categoryIds.add("not-found");c.normalize(s);ok(c.byId.containsKey(s.guideId)&&s.people!=999&&!s.categoryIds.contains("not-found"),"Stale preferences migrate safely");
    s.opacity=0;s.color=com.hoonjay.filmroom.core.GuideAppearance.COLORS[2];GuideCatalog.Selection hidden=c.restore(s.json().toString());ok(hidden.opacity==0&&hidden.color==s.color,"Zero opacity and black survive persistence");
    s.opacity=999;s.color=123;c.normalize(s);ok(s.opacity==100&&s.color==com.hoonjay.filmroom.core.GuideAppearance.COLORS[0],"Invalid appearance values normalized");
    System.out.println("PASS: "+checks+" native catalog/keyword/selection/layout checks");
  }
}

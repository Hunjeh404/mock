package com.hoonjay.filmroom.core;
import java.util.*;
/** Presentation order never changes built-in recipes or deletes hidden custom recipes. */
public final class PresetCatalog {
  private PresetCatalog(){}
  public static List<Recipe> ordered(List<Recipe> all,List<String> order){
    Map<String,Recipe> byId=new LinkedHashMap<>();for(Recipe r:all)byId.put(r.id,r);
    List<Recipe> result=new ArrayList<>();for(String id:order){Recipe r=byId.remove(id);if(r!=null)result.add(r);}
    result.addAll(byId.values());return result;
  }
  public static List<Recipe> visible(List<Recipe> all,List<String> order,Set<String> hidden){
    List<Recipe> result=new ArrayList<>();for(Recipe r:ordered(all,order))if(!hidden.contains(r.id))result.add(r);
    if(result.isEmpty()&&!all.isEmpty())result.add(all.get(0));return result;
  }
}

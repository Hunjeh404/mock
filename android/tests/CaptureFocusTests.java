import com.hoonjay.filmroom.core.CaptureFocusGate;
import static com.hoonjay.filmroom.core.CaptureFocusGate.Decision.*;
public class CaptureFocusTests{
 static int checks;static void ok(boolean x,String m){checks++;if(!x)throw new AssertionError(m);}
 static void eq(CaptureFocusGate.Decision x,CaptureFocusGate.Decision y,String m){ok(x==y,m+": "+x);}
 public static void main(String[] args){
  CaptureFocusGate g=new CaptureFocusGate(1000);
  eq(g.observe(1010,20,4,false),WAIT,"pre-trigger focused frame cannot shoot");g.triggered(25);
  eq(g.observe(1020,24,4,false),WAIT,"queued old frame ignored");
  eq(g.observe(1030,25,3,true),WAIT,"active scan waits");
  eq(g.observe(1040,26,4,true),WAIT,"focus lock with moving lens waits");
  eq(g.observe(1050,27,4,false),READY,"stationary focused lock shoots");
  eq(g.observe(1060,28,1,true),READY,"ready is terminal, one submission");
  g=new CaptureFocusGate(0);g.triggered(10);eq(g.observe(100,10,5,false),RETRY,"failed AF retried once");
  eq(g.observe(110,9,4,false),WAIT,"retry cannot reuse old success");eq(g.observe(120,11,4,false),WAIT,"retry must have START result");
  g.triggered(13);eq(g.observe(200,13,4,false),READY,"retry success");
  g=new CaptureFocusGate(0);g.triggered(10);eq(g.observe(100,10,5,false),RETRY,"first failure");g.triggered(12);eq(g.observe(200,12,5,false),FAILED,"second failure does not capture");
  g=new CaptureFocusGate(0);g.triggered(20);eq(g.observe(100,20,1,true),WAIT,"continuous scan waits");eq(g.poll(1800),FAILED,"scan timeout does not freeze moving lens");
  g=new CaptureFocusGate(0);g.triggered(20);eq(g.observe(100,20,2,false),WAIT,"passive focus waits for explicit lock");eq(g.poll(1800),READY,"bounded passive-focused fallback");
  g=new CaptureFocusGate(0);g.triggered(20);eq(g.observe(100,20,null,false),WAIT,"missing AF state not early confirmation");eq(g.poll(1800),UNREPORTED,"missing-state fallback keeps AF enabled");
  g=new CaptureFocusGate(0);g.triggered(20);g.observe(100,20,null,true);eq(g.poll(1800),FAILED,"unknown AF plus lens movement blocks still");
  g=new CaptureFocusGate(0);eq(g.poll(1800),FAILED,"lost START callback cannot hang shutter");
  g=new CaptureFocusGate(0);g.triggered(20);g.observe(100,21,1,true);eq(g.observe(110,20,4,false),WAIT,"out-of-order focused frame ignored");eq(g.poll(1800),FAILED,"out-of-order success cannot authorize capture");
  g=new CaptureFocusGate(0);g.triggered(20);g.observe(100,20,4,true);eq(g.observe(120,21,null,false),READY,"known locked AF plus later lens settlement");
  ok(CaptureFocusGate.required(false,-1,false,false,false,false,false,true),"normal AUTO camera waits");
  ok(!CaptureFocusGate.required(false,.5f,false,false,false,false,false,true),"manual focus never rescanned");
  ok(!CaptureFocusGate.required(false,0,false,false,false,false,false,true),"manual infinity retained");
  ok(!CaptureFocusGate.required(false,-1,true,false,false,false,false,true),"star/firework far focus retained");
  ok(!CaptureFocusGate.required(true,-1,false,false,false,false,false,true),"vendor portrait not overridden");
  ok(!CaptureFocusGate.required(false,-1,false,false,false,false,false,false),"fixed-focus lens does not wait");
  ok(!CaptureFocusGate.required(false,-1,false,true,false,false,false,true),"explicit hold focus retained without distance metadata");
  ok(CaptureFocusGate.required(false,-1,false,true,true,false,false,true),"tracking exposure lock still permits AF confirmation");
  ok(!CaptureFocusGate.required(false,-1,false,true,true,true,false,true),"lost tracking preserves selected hold");
  ok(!CaptureFocusGate.required(false,-1,false,true,true,false,true,true),"held lens distance is not refocused");
  System.out.println("PASS: "+checks+" shutter AF sequence/policy checks");
 }
}

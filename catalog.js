/* Press Frame catalog v0.1.0 — dependency-free, shared many-to-many mapping. */
(function(root){
'use strict';
const G=(id,name,keywords,tip,people=[2,3,4,6],base=2)=>({id,name,keywords:keywords.split(' '),tip,people,base});
const guides=[
G('portrait','인물 프로필','대표 임원 취임 인사 프로필 얼굴 상반신','눈높이를 맞추고 머리 위와 어깨 양옆에 여백을 남겨요.',[1],1),
G('small-group','소수 인원 기념촬영','기념 관계자 방문 출범 나란히 단체','중앙을 맞추고 사람 사이 간격을 일정하게 배치해요.'),
G('large-group','다수 인원 단체사진','단체 임직원 구성원 봉사 행사 전체','앞뒤 줄의 얼굴이 겹치지 않게 서고 양끝 여백을 확보해요.',[6,8,12],8),
G('held-prop','문서·전달판·상패 들기','협약서 인증서 상패 전달판 기부 기념촬영 문서 MOU 업무협약','소품을 가슴 아래에 두고 글자가 손에 가리지 않게 해요.',[1,2,3,4,6],2),
G('handshake','악수','협약 계약 제휴 악수 파트너십 체결','두 사람의 얼굴과 맞잡은 손을 함께 담아요.',[2],2),
G('signing','서명','계약 서명 협약 체결 사인','얼굴과 문서, 펜을 든 손이 모두 보이게 잡아요.',[1,2,3,4],2),
G('handover','수여·전달','수여 전달 수상 기부 후원 임명 장학금','전달물을 중앙에 두고 양쪽 손과 표정이 보이게 해요.',[2],2),
G('ribbon','테이프 커팅','개소 개관 준공 개장 오픈 테이프 가위','테이프와 가위를 같은 높이에 두고 전신을 담아요.',[3,4,6,8],6),
G('unveil','현판·기념물 제막','현판 제막 개소 출범 기념물','현판의 글자가 드러나도록 양옆에 서요.',[2,4,6],2),
G('shovel','시삽','착공 시삽 건립 삽 투자','삽과 흙, 참여자의 전신이 잘리지 않게 담아요.',[3,4,6,8],4),
G('product','제품 단독','제품 신제품 단말 기기 디바이스 장비 상패 결과물','제품의 수직·수평을 맞추고 가장자리 여백을 확보해요.',[0],0),
G('model-product','모델이 제품·서비스 소개','출시 모델 소개 요금제 혜택 프로모션 스마트폰','얼굴과 제품이 경쟁하지 않도록 좌우로 나눠 배치해요.',[1,2],1),
G('demo','사용·시연','시연 사용 체험 이용 서비스 기술 실증 AI 인공지능 화면','손동작과 화면이 함께 보이는 방향에서 촬영해요.',[1,2,3],2),
G('speaker','발표자·연설자','발표 연설 취임사 브리핑 콘퍼런스 세미나 기자간담회','발표자와 행사명을 함께 담고 마이크가 얼굴을 가리지 않게 해요.',[1],1),
G('audience','무대·객석 전경','객석 무대 포럼 행사 콘퍼런스 세미나 설명회','무대를 수평으로 맞추고 객석과 행사명이 함께 보이게 해요.',[0],0),
G('booth','전시 부스·상담','부스 전시 박람회 MWC CES 상담 전시회','부스명과 시연물, 설명자와 관람객을 함께 담아요.',[2,3,4],2),
G('exterior','건물·시설 외관','건물 외관 사옥 시설 공장 부지 매장 준공','건물 수직선을 맞추고 간판과 건물 끝이 잘리지 않게 해요.',[0],0),
G('interior','시설·공간 내부','내부 공간 센터 연구소 관제실 설비 매장 시설','수평·수직을 맞추고 주요 설비와 공간의 깊이를 보여줘요.',[0],0),
G('activity','작업·연구·봉사 활동','연구 실험 개발 봉사 포장 환경정화 작업 점검 복구','사람의 얼굴뿐 아니라 손과 작업 대상을 함께 담아요.',[1,2,3,4],2),
G('interaction','상담·교육·교류','교육 상담 교류 고객 지원 수혜자 봉사 수업','두 사람의 시선과 함께 보는 자료가 자연스럽게 이어지게 해요.',[2,3,4],2)
];
const R=(id,group,name,keywords,primary,extra)=>({id,group,name,keywords:keywords.split(' '),primary:primary.split(' '),extra:extra?extra.split(' '):[]});
const releases=[
R('product-launch','출시·기술','제품 출시','신제품 출시 단말 기기 디바이스 스마트폰 공개','product model-product demo','booth'),
R('service-launch','출시·기술','서비스·요금제·혜택 출시','서비스 요금제 혜택 프로모션 구독 출시 AI 인공지능 앱','model-product demo interaction','booth'),
R('research','출시·기술','기술 개발·연구 성과','기술 개발 연구 성과 성공 AI 인공지능 로봇 특허','activity demo product','interior small-group'),
R('pilot','출시·기술','실증·시범 운영','실증 시범 테스트 운영 검증 자율주행 AI 인공지능','demo activity','interior exterior small-group'),
R('agreement','협력·시설','협약·계약·제휴','업무협약 MOU 협약 계약 제휴 체결 파트너십 공동 협력 공급계약','held-prop handshake signing small-group','demo interior interaction'),
R('opening','협력·시설','개소·개관·준공','개소 개관 준공 개장 오픈 센터 연구소 완공','exterior interior ribbon unveil small-group','product demo'),
R('investment','협력·시설','투자·착공·사업 추진','투자 착공 사업 추진 건립 공장 부지 인프라','shovel exterior speaker small-group','signing handshake activity'),
R('event','행사·성과','행사 개최·참가','행사 전시 박람회 콘퍼런스 컨퍼런스 간담회 설명회 포럼 세미나 MWC CES 대회','audience speaker booth large-group','demo interaction handover'),
R('award','행사·성과','수상·인증·선정','수상 인증 선정 인증서 상패 평가 우수 시상','held-prop handover small-group','product activity audience'),
R('milestone','행사·성과','판매·가입자·사업 성과','판매 가입자 달성 기록 돌파 실적 성과 백만 천만','model-product demo held-prop','activity small-group'),
R('anniversary','행사·성과','창립·사업 주년 기념','창립 주년 기념 생일 역사','large-group held-prop audience','exterior interaction activity'),
R('donation','사회공헌','기부·후원·전달','기부 후원 성금 물품 장학금 전달 지원 전달식','held-prop handover small-group','activity interaction'),
R('volunteer','사회공헌','봉사·사회공헌·캠페인','봉사 사회공헌 캠페인 환경정화 교육 지원 취약계층 나눔','activity interaction large-group','handover exterior'),
R('customer','고객·조직','고객 도입·구축 사례','고객 도입 구축 적용 사례 공급 B2B 고객사','demo interior activity','interaction small-group product'),
R('personnel','고객·조직','인사·취임','인사 취임 대표 CEO 임원 사장 선임 임명','portrait speaker handover','audience interaction'),
R('organization','고객·조직','조직·법인·추진단 출범','조직 법인 추진단 출범 구성 신설 출범식','large-group small-group speaker unveil','interior activity portrait'),
R('policy','기타 발표','정책·제도·운영 변경','정책 제도 운영 변경 개편 약관','demo interaction activity','speaker'),
R('report','기타 발표','조사·통계·보고서 발표','조사 통계 보고서 전망 설문 분석 백서','speaker activity','portrait'),
R('finance','기타 발표','경영 실적·재무·주주 안내','경영 재무 주주 배당 자사주 매출 영업이익 실적','speaker exterior activity','portrait'),
R('response','기타 발표','입장·해명·사고 대응','입장 해명 사고 대응 장애 복구 점검 재발방지','speaker activity','exterior interior')
];
const normalize=s=>String(s||'').toLocaleLowerCase().replace(/[^\p{L}\p{N}]/gu,'');
const stop=new Set(['사진','보도자료','추천','촬영','가이드','보도','관련']);
const terms=q=>[...new Set(String(q||'').toLocaleLowerCase().split(/[^\p{L}\p{N}]+/u).map(normalize).filter(x=>x&&!stop.has(x)))];
function hits(item,query){const t=terms(query);return t.filter(token=>[item.name,...item.keywords].some(v=>normalize(v).includes(token)||(normalize(v).length>1&&token.includes(normalize(v)))));}
function recommend(ids=[],query=''){
 const matched=releases.map(r=>({...r,hits:hits(r,query)})).filter(r=>r.hits.length);
 const chosen=releases.filter(r=>ids.includes(r.id));
 const rows=guides.map(g=>{let score=0;const reasons=[];const direct=hits(g,query);
  for(const r of chosen){const p=r.primary.indexOf(g.id);if(p>=0){score+=40-p;reasons.push({type:'category',label:r.name,primary:true,id:r.id});}else if(r.extra.includes(g.id)){score+=12;reasons.push({type:'category',label:r.name,primary:false,id:r.id});}}
  for(const r of matched){const p=r.primary.indexOf(g.id);if(p>=0){score+=25*r.hits.length-p;reasons.push({type:'keyword-category',label:r.name,primary:true,id:r.id});}else if(r.extra.includes(g.id)){score+=5*r.hits.length;reasons.push({type:'keyword-category',label:r.name,primary:false,id:r.id});}}
  if(direct.length){score+=direct.length*60;reasons.unshift({type:'keyword',label:direct.join(' · '),primary:true});}
  return {...g,score,reasons,direct};
 }).filter(g=>g.score>0).sort((a,b)=>b.score-a.score||guides.indexOf(guides.find(x=>x.id===a.id))-guides.indexOf(guides.find(x=>x.id===b.id)));
 return {guides:rows,categories:matched,tokens:terms(query)};
}
root.PressGuideCatalog=Object.freeze({version:'0.1.0',guides,releases,normalize,terms,hits,recommend});
if(typeof module!=='undefined')module.exports=root.PressGuideCatalog;
})(typeof window!=='undefined'?window:globalThis);

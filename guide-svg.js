/* Code-drawn composition guides, not generated photographs. */
(function(root){'use strict';
function svg(id,n=2,prop='individual'){
const parts=[];const add=x=>parts.push(x);
const line=(x,y,a,b,c='guide')=>add(`<path class="${c}" d="M${x} ${y}L${a} ${b}"/>`);
const rect=(x,y,w,h,r=8,c='guide')=>add(`<rect class="${c}" x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}"/>`);
const text=(x,y,t)=>add(`<text x="${x}" y="${y}" class="label" text-anchor="middle">${t}</text>`);
function person(x,y,s=1,seated=false){add(`<g transform="translate(${x} ${y}) scale(${s})"><ellipse class="guide" cx="0" cy="0" rx="30" ry="38"/><path class="guide" d="M-20 36L-20 53Q-68 56-76 91L-88 178M20 36L20 53Q68 56 76 91L88 178M-55 80L-53 200L53 200L55 80"/>${seated?'':'<path class="guide" d="M-53 200L-45 310L-8 310L0 211L8 310L45 310L53 200"/>'}<path class="soft" d="M-30-4H30"/></g>`);}
function row(count,y=185,scale=.8){const gap=Math.min(195,760/Math.max(count,1));for(let i=0;i<count;i++)person(500+(i-(count-1)/2)*gap,y,scale);}
add('<rect class="safe" x="45" y="38" width="910" height="544" rx="6"/>');
line(45,108,955,108,'soft');text(500,83,'배경 제목 · 로고 확보');
if(id==='portrait'){person(500,235,1.8,true);line(290,228,710,228,'soft');}
else if(id==='small-group')row(n);
else if(id==='large-group'){const back=Math.ceil(n/2),front=n-back;row(back,186,.66);row(front,304,.66);}
else if(id==='held-prop'){row(n,180,.86);if(n===1||prop==='shared'){rect(n===1?418:285,298,n===1?164:430,110);text(500,350,'소품 · 글자 노출');}else{const gap=Math.min(195,760/n);for(let i=0;i<n;i++){const x=500+(i-(n-1)/2)*gap;rect(x-58,290,116,100);line(x,290,x,390,'soft');}text(500,440,'문서는 같은 높이로');}}
else if(id==='handshake'){person(337,188,.92);person(663,188,.92);add('<path class="guide" d="M386 270L468 330L500 324L532 330L614 270M391 292L468 351L500 342L532 351L609 292"/>');rect(455,311,90,55);text(500,447,'얼굴과 맞잡은 손');}
else if(id==='signing'){row(n,195,.86);rect(200,355,600,130);for(let i=0;i<n;i++){const x=500+(i-(n-1)/2)*Math.min(190,600/n);rect(x-52,370,104,65);line(x+20,378,x+45,345);}text(500,520,'문서 · 펜 · 손');}
else if(id==='handover'){person(320,192,.9);person(680,192,.9);rect(430,300,140,100);line(370,280,437,345);line(630,280,563,345);text(500,455,'전달물과 양쪽 손');}
else if(id==='ribbon'){row(n,180,.75);line(100,332,900,332);line(100,345,900,345);for(let i=0;i<n;i++){const x=500+(i-(n-1)/2)*Math.min(195,760/n);add(`<path class="guide" d="M${x-14} 319l28 30m-28 0l28-30"/>`);}text(500,510,'테이프 높이 통일');}
else if(id==='unveil'){rect(325,160,350,235);text(500,262,'현판 노출');person(205,210,.83);person(795,210,.83);if(n>2){person(90,225,.7);person(910,225,.7);}line(320,150,285,390);line(680,150,715,390);}
else if(id==='shovel'){row(n,175,.72);for(let i=0;i<n;i++){const x=500+(i-(n-1)/2)*Math.min(195,760/n);line(x+30,295,x+60,460);add(`<path class="guide" d="M${x+37} 448l46-8-1 44q-17 25-34 1z"/>`);}line(100,495,900,495,'soft');text(500,540,'삽 · 흙 · 발끝 포함');}
else if(id==='product'){rect(350,165,300,340,22);line(500,135,500,530,'soft');line(250,510,750,510);text(500,563,'제품 가장자리 여백');}
else if(id==='model-product'){person(350,205,1.05,true);rect(635,235,145,210,16);line(420,320,630,360);if(n>1)person(850,210,.62);text(675,490,'제품 · 소개 화면');}
else if(id==='demo'){person(280,205,.9);if(n>1)person(755,215,.85);rect(400,220,210,160);line(505,380,505,420);line(455,420,555,420);line(333,285,415,338);rect(350,425,300,15,2);text(505,180,'화면 노출');if(n>2)person(895,225,.65);}
else if(id==='speaker'){rect(100,145,405,170);text(302,220,'행사명 · 발표 주제');person(700,225,.9,true);rect(575,365,250,160);line(675,365,680,300);line(680,300,723,300);}
else if(id==='audience'){rect(160,170,680,200);text(500,235,'무대 · 행사명');person(500,282,.22,true);line(130,390,870,390);for(let r=0;r<2;r++)for(let c=0;c<8;c++){add(`<ellipse class="guide" cx="${185+c*90}" cy="${440+r*68}" rx="23" ry="26"/>`);}}
else if(id==='booth'){rect(150,135,700,65);text(500,177,'부스명 · 로고');line(150,200,150,530);line(850,200,850,530);person(320,285,.65);person(705,300,.67);rect(430,290,140,105);rect(395,405,215,100);}
else if(id==='exterior'){rect(250,150,500,360,2);rect(370,180,260,55);text(500,216,'간판');for(let r=0;r<3;r++)for(let c=0;c<5;c++)rect(280+c*92,265+r*70,60,40,1);line(80,515,920,515);line(250,110,250,550,'soft');line(750,110,750,550,'soft');}
else if(id==='interior'){add('<path class="guide" d="M80 150L335 275H665L920 150M80 555L335 430H665L920 555M335 275V430M665 275V430"/>');rect(370,300,90,110);rect(490,300,120,110);text(500,490,'주요 설비 · 공간 깊이');}
else if(id==='activity'){person(310,202,.9);if(n>1)person(720,222,.8);if(n>2)person(875,240,.6);rect(350,375,300,100);rect(440,325,120,65);line(363,280,450,350);line(676,290,554,350);text(500,522,'손과 작업 대상');}
else if(id==='interaction'){person(275,215,.9);person(725,215,.9);if(n>2)person(885,240,.62);rect(425,330,150,95);line(330,300,440,370);line(670,300,560,370);text(500,490,'시선 · 대화 · 자료');}
return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 620" role="img" aria-label="촬영 구도 가이드"><style>.guide{fill:none;stroke:currentColor;stroke-width:2.8;stroke-linecap:round;stroke-linejoin:round}.soft{fill:none;stroke:currentColor;stroke-width:1.3;opacity:.38;stroke-dasharray:7 9}.safe{fill:none;stroke:currentColor;stroke-width:1;opacity:.25;stroke-dasharray:8 10}.label{fill:currentColor;stroke:none;font:17px sans-serif;opacity:.85}</style>${parts.join('')}</svg>`;
}
root.PressGuideSVG=svg;
if(typeof module!=='undefined')module.exports=svg;
})(typeof window!=='undefined'?window:globalThis);

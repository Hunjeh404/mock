(function(){'use strict';
const picker=document.querySelector('press-guide-picker');
const overlay=document.querySelector('#overlay');let visible=true,current;let toastTimer;
function display(p){current=p;overlay.innerHTML=p.svg;document.querySelector('#guide-title').textContent=p.guideName;const g=PressGuideCatalog.guides.find(x=>x.id===p.guideId);document.querySelector('#guide-tip').textContent=g.tip;const select=document.querySelector('#people');select.innerHTML=g.people.map(n=>`<option value="${n}">${n===0?'인물 없음':n+'명'}</option>`).join('');select.value=p.people;select.disabled=g.people.length===1;document.querySelector('.prop-option').hidden=p.guideId!=='held-prop';document.querySelector('#prop').value=p.prop;document.querySelector('#draft-badge').textContent=picker.isOpen?'미리보기':'적용됨';}
picker.addEventListener('press-guide-preview',e=>display(e.detail));
picker.addEventListener('press-guide-open',e=>{picker.toggleAttribute('data-open',e.detail.open);document.querySelector('#draft-badge').textContent=e.detail.open?'미리보기':'적용됨';});
picker.addEventListener('press-guide-change',e=>{try{localStorage.setItem('press-frame-selection-v1',JSON.stringify({guideId:e.detail.guideId,categoryIds:e.detail.categoryIds,people:e.detail.people,prop:e.detail.prop}));}catch{}const toast=document.querySelector('#toast');toast.textContent=e.detail.guideName+' 가이드를 적용했어요';toast.classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>toast.classList.remove('visible'),2600);});
document.querySelector('#people').addEventListener('change',e=>picker.setOptions({people:Number(e.target.value)}));
document.querySelector('#prop').addEventListener('change',e=>picker.setOptions({prop:e.target.value}));
document.querySelector('#opacity').addEventListener('input',e=>{if(visible)overlay.style.opacity=e.target.value/100;});
document.querySelector('#overlay-toggle').addEventListener('click',e=>{visible=!visible;e.currentTarget.textContent=visible?'가이드 켜짐':'가이드 꺼짐';e.currentTarget.setAttribute('aria-pressed',visible);overlay.style.opacity=visible?document.querySelector('#opacity').value/100:0;});
try{const saved=JSON.parse(localStorage.getItem('press-frame-selection-v1'));if(saved)picker.setSelection(saved);}catch{}
display(picker.getSelection());picker.open();
})();

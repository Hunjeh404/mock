# Press Frame — 가이드 선택 UI 0.1.0

보도자료 분류와 키워드를 동시에 활용해 사진 구도를 고르는 독립형 UI입니다.
기존 카메라 엔진과 분리되어 있으며, 이 패키지에는 촬영·저장·실시간 카메라 기능은 포함하지 않습니다.

## 실행

웹 미리보기: https://hunjeh404.github.io/mock/

저장소를 다운로드한 뒤 `index.html`을 열어도 됩니다. 인터넷, 설치, API 키가 필요하지 않습니다.

## 선택 흐름

1. 가이드 선택 버튼을 누르면 아래로 메뉴가 펼쳐집니다. 시안에서는 메뉴가 열린 상태로 시작합니다.
2. 왼쪽 보도자료 분류를 선택하면 오른쪽에 해당 사진 후보가 표시됩니다.
3. 분류는 여러 개 선택할 수 있습니다. 겹치는 사진은 한 번만 표시됩니다.
4. 키워드를 입력하면 해당 분류·사진을 찾고 기존 분류 추천과 합쳐 우선순위를 정합니다.
5. 사진을 고르면 구도 미리보기가 변경됩니다. ‘가이드 적용’을 누르면 선택이 확정됩니다.
6. 선택 버튼 재클릭, 바깥 클릭, Escape로 닫으면 적용 전 변경을 취소합니다.
7. ‘전체 20’에서 추천과 관계없이 원하는 사진을 직접 선택할 수 있습니다.

‘추천’은 편집 가능한 로컬 키워드 사전 및 다대다 분류 매핑을 사용합니다. 외부 AI나 서버 요청은 없습니다.
분류 선택과 검색은 교집합 필터가 아니라 합산 추천입니다. 예를 들어 ‘협약’을 선택한 상태에서 ‘봉사’를 검색하면 협약 사진과 봉사 관련 사진을 함께 제안합니다.
검색에 일치하지 않으면 그 사실을 표시하고 선택된 분류의 추천만 유지합니다.
선택한 사진은 추천 목록이 바뀌어도 임의로 교체하지 않습니다. 하단 ‘선택한 사진’에서 확인 후 확정할 수 있습니다.

## 파일

- `catalog.js`: 20개 보도자료 분류, 20개 사진 형태, 키워드, 추천·보완 매핑
- `guide-svg.js`: 구도 미리보기 SVG 생성 함수
- `press-guide-picker.js`: Shadow DOM을 사용하는 독립 Web Component
- `index.html`, `demo.css`, `demo.js`: 확인용 화면. 카메라 앱 통합 시 교체 가능

## 웹 또는 WebView에 붙이기

```html
<script src="catalog.js"></script>
<script src="guide-svg.js"></script>
<script src="press-guide-picker.js"></script>
<press-guide-picker id="picker"></press-guide-picker>
<div id="camera-overlay" style="color:#d4f1ce"></div>
<script>
const picker = document.querySelector('#picker');
const overlay = document.querySelector('#camera-overlay');
picker.addEventListener('press-guide-preview', event => {
  // 선택 중 미리보기 및 취소 시 이전 가이드 복원.
  overlay.innerHTML = event.detail.svg;
});
picker.addEventListener('press-guide-change', event => {
  // 확정된 선택만 여기서 저장하거나 카메라 엔진에 전달합니다.
  console.log(event.detail);
});
picker.setSelection({
  categoryIds: ['agreement', 'service-launch'],
  guideId: 'demo', people: 2, prop: 'individual'
});
</script>
```

프로그램 API:

- `picker.open()` / `picker.close()`
- `picker.getSelection()`: 적용된 상태를 반환. 적용 전 임시 선택과 구분
- `picker.setSelection({categoryIds, guideId, people, prop})`: 초기 또는 외부 선택 복원
- `picker.setOptions({people, prop})`: 인원·소품 배치 변경
- `press-guide-open`: `{open: boolean}`
- `press-guide-preview`: 임시 미리보기 변경 이벤트
- `press-guide-change`: 적용 시 확정 이벤트

팝업과 함께 조작할 외부 인원·소품 컨트롤에는 `data-press-guide-companion` 속성을 붙이면 바깥 클릭에 의한 취소 대상에서 제외됩니다.
분류와 키워드 자체의 임시 변경은 가이드 적용 때 확정됩니다. 샘플의 localStorage 저장은 `demo.js`에만 있으며, 선택 컴포넌트 자체는 저장소에 접근하지 않습니다.

## 확정 이벤트 데이터

```json
{
  "schemaVersion": 1,
  "guideId": "demo",
  "guideName": "사용·시연",
  "categoryIds": ["agreement", "service-launch"],
  "people": 2,
  "prop": "individual",
  "svg": "<svg ...>...</svg>"
}
```

SVG 기준 영역은 1000×620입니다. 실제 카메라 화면의 종횡비, 미러링, 회전, 사진 크롭 영역을 확인하여 투영해야 합니다.
현 단계의 인물 윤곽은 구도를 확인하는 시안입니다. 실제 사람의 자세를 감지하거나 자동 정렬하지 않습니다.
확정된 가이드 ID를 넘기므로 카메라 앱의 네이티브 가이드 렌더러로 SVG를 대체해도 됩니다.

## Android 연결 지점

WebView 쪽에서 `window.PressGuideNative.onGuideSelected(json)` 브리지를 등록하면 ‘가이드 적용’ 때 JSON 문자열을 전달합니다.
브리지는 해당 로컬 UI에만 연결하세요. 이 패키지에는 네이티브 카메라 수정이나 APK 빌드가 포함되어 있지 않습니다.
기존 앱이 WebView 기반이 아니면 catalog의 매핑과 선택 데이터 계약을 재사용하여 네이티브 선택 UI로 옮길 수 있습니다.

## 확인한 항목

- 20개 분류와 20개 사진의 매핑 유효성 및 중복 제거
- 복수 분류 + 한국어·영문 키워드 동시 추천, 검색 결과 없음 처리
- 사진 선택과 인원 변경에 따른 미리보기
- 적용, 재선택, Escape 취소 시 이전 상태 복원
- 데스크톱 화면과 390×844 모바일 프레임에서 검색·선택·적용
- 모바일 팝업 높이 제한과 내부 스크롤, 적용 버튼 노출

현재 기존 카메라 앱과 결합하기 전의 선택 UI 및 연결용 소스입니다.

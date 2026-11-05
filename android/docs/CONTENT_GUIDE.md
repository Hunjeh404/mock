# 분류·가이드 콘텐츠 수정 방법

앱의 코드를 읽기 전에 `app/src/main/assets/press-camera.manifest.json`의 `content` 경로를 확인하세요. 아래 경로는 현재 1.0.0 기준입니다. 앱은 로컬 JSON만 읽으며 수정 후 APK를 다시 빌드해야 합니다.

## 1. 안내 문구·이름·검색어 바꾸기

`press-guides/catalog.json`의 `guides`에서 대상 사진의 `id`를 찾습니다.

```json
{
  "id": "handshake",
  "name": "악수",
  "keywords": ["협약", "계약", "제휴", "악수", "파트너십", "체결"],
  "tip": "두 사람의 얼굴과 맞잡은 손을 함께 담아요.",
  "peopleOptions": [2],
  "defaultPeople": 2,
  "layoutId": "handshake"
}
```

이름·tip·keywords만 바꿀 때 ID는 유지합니다. 인원은 `peopleOptions`, 기본값은 `defaultPeople`을 기준으로 작성합니다.

## 2. 보도자료 분류 추가하기

같은 파일의 `categories` 배열에 추가합니다.

```json
{
  "id": "factory-tour",
  "group": "협력·시설",
  "name": "생산시설 현장 방문",
  "keywords": ["공장", "현장방문", "견학"],
  "primary": ["activity", "interior"],
  "extra": ["small-group", "exterior"]
}
```

primary는 대표사진 후보, extra는 보완사진 후보입니다. 여러 분류에 같은 사진 ID를 넣어도 됩니다. 앱은 ID로 합쳐 한 번만 표시합니다.

## 3. 추천·검색 동작

- 선택한 분류와 키워드로 찾은 분류/사진을 함께 추천합니다. 교집합 필터가 아닙니다.
- 직접 사진 키워드 일치를 우선하고, 분류의 primary와 extra에 서로 다른 점수를 줍니다.
- 여러 분류를 선택하면 공통으로 연결되는 사진의 점수가 합산됩니다.
- 영문은 대소문자를 구분하지 않습니다. MOU처럼 실제로 쓸 약어를 keywords에 추가하세요.
- 외부 AI 의미 검색은 없으므로 새 표현은 동의어를 사전에 넣어야 합니다.
- 키워드에 맞는 것이 없으면 ‘일치 없음’을 표시하고 선택한 분류의 추천을 유지합니다. ‘전체’에서 직접 고를 수도 있습니다.
- 추천 결과가 바뀌어도 사용자가 고른 사진을 임의로 바꾸지 않습니다. 적용/취소 상태는 분리됩니다.

점수 규칙은 `GuideCatalog.recommend` 한 곳에 있습니다. 화면과 엔진에 같은 로직을 복제하지 마세요.

## 4. 사진 형태 새로 추가하기

1. `catalog.json`의 guides에 새 항목과 안정적인 ID를 추가합니다.
2. `press-guides/layouts/index.json`의 layoutFiles에 새 ID와 파일 경로를 추가합니다.
3. 해당 `layouts/<id>.json`을 추가합니다.
4. 적절한 categories.primary/extra에 연결합니다.
5. catalogVersion을 올리고 검증합니다.

도형 파일 구조:

```json
{
  "schemaVersion": 1,
  "layoutId": "new-photo",
  "variants": {
    "2:individual": {
      "landscape": [
        {"type": "rect", "style": "guide", "x": 0.1, "y": 0.2, "width": 0.8, "height": 0.6, "radius": 0.02},
        {"type": "ellipse", "style": "guide", "x": 0.5, "y": 0.4, "rx": 0.06, "ry": 0.09},
        {"type": "path", "style": "soft", "commands": [["M", 0.1, 0.5], ["L", 0.9, 0.5]]},
        {"type": "text", "style": "label", "x": 0.5, "y": 0.85, "text": "소품을 같은 높이로"}
      ],
      "portrait": []
    }
  }
}
```

위 예시의 빈 portrait는 설명을 위한 자리입니다. 실제 추가 시 세로 구도를 채워야 합니다. `peopleOptions`의 각 인원에 대응하는 키가 필요합니다. 문서/전달판 들기(`held-prop`)는 `N:individual`(각자 들기), `N:shared`(가운데 하나)를 모두 제공합니다.

좌표는 각 방향의 화면 너비/높이를 1로 본 정규 좌표입니다. ellipse의 rx는 너비, ry는 높이 기준입니다. 원을 유지하려면 가로·세로 화면의 물리 비율을 고려해 별도로 작성하세요. 촬영 화면은 항상 landscape 3:2 구도를 사용합니다. portrait 데이터는 기존 데이터 호환과 향후 확장을 위해 유지합니다. 가이드는 사진 픽셀에 그리지 않습니다.

path 명령은 절대 좌표 M(2개), L(2개), Q(4개), C(6개), Z(0개)를 지원합니다. 상대 좌표, SVG transform, arc는 지원하지 않습니다. 새 타입이 필요하면 renderer와 validator를 함께 확장하세요.

## 5. 보도 클린 프리셋 조정

`press-guides/press-clean.json`을 수정합니다. 자연스러운 색, 하이라이트 보호, 작은 디테일 보강과 약한 노이즈 완화를 기본으로 합니다. grain/bloom/vignette/피부 형태 보정은 쓰지 않습니다. detail은 0~0.3 범위이며 현재 0.16입니다.

프리셋은 JPG 및 미리보기에 적용되고 RAW 센서 값에는 적용되지 않습니다. 노이즈/디테일 값을 크게 올리면 디테일 손실이나 처리 시간 증가가 생길 수 있으므로 샘플과 실제 단말로 확인하세요.

## 6. 검증

```bash
python3 scripts/validate-guides.py
bash scripts/test-guides.sh
```

APK 빌드도 데이터 검증을 자동 실행합니다. 참조가 없는 ID, 빠진 도형 파일, 지원하지 않는 path 명령은 배포 전에 수정하세요.

## 7. 경고 문구와 가이드 외형

명부·암부 안내 문구는 제품 매니페스트의 warnings.highlights/shadows에서 수정합니다. 검은 배경과 테마색 글씨는 CameraOverlay의 화면 표시 정책입니다. 가이드 색상 목록은 core/GuideAppearance, 투명도 기본값은 GuideCatalog.Selection에서 관리합니다. 저장 키 opacity/color는 기존 ID와 마찬가지로 마이그레이션 없이 바꾸지 않습니다.

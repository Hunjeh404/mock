# 구조와 데이터 흐름

## 폴더

- `MainActivity.java`: 촬영 화면, 자동/프로 전환, 생명주기, 갤러리 확인·공유·단일/다중 삭제
- `camera/CameraEngine.java`: RAW 지원 후면 렌즈 탐색, Camera2 요청, AF 대기, 결과/이미지 타임스탬프 대응, DNG 저장
- `camera/CameraPreview.java`: GLSL 미리보기와 중립 측광/추적 입력
- `ui/CameraOverlay.java`: 네이티브 캔버스 HUD, 프로 다이얼, 가이드 버튼과 화면 가이드
- `guides/GuideCatalog.java`: JSON 로딩·검증, 다대다 추천, 검색·저장 선택 복원
- `guides/GuidePicker.java`: 아래로 펼치는 네이티브 팝업, 왼쪽 분류/오른쪽 사진, 키워드 검색, 적용/취소
- `guides/GuideRenderer.java`: 파일의 정규 도형을 캔버스에 표시
- `guides/PressConfig.java`: 제품 매니페스트와 단일 프리셋 로딩
- `storage/ShotProcessor.java`: RAW를 먼저 공용 사진첩에 확보하고 JPG를 순차 처리, 미완료 복구
- `storage/Stores.java`: 앱별 촬영 기록과 MediaStore 저장
- `core/Developer.java`, `FilmShader.java`: CPU/GPU 프리셋 처리
- `core/DetailEnhancement.java`: 제한된 밝기 디테일 보강. 가로/세로 읽기를 지원하는 타일 캐시
- `core/DngCrop.java`: 원본 센서 샘플을 그대로 둔 채 DNG 기본 크롭 메타데이터 설정

기존 편집 화면 클래스는 제거했습니다. ShotProcessor가 쓰는 내부 변환·JSON·원본 접근 유틸 일부는 남아 있지만 앱에서 편집실·프리셋 관리로 진입하는 경로는 없습니다.

## 촬영

1. 사용자 가이드 선택이 적용되면 선택 ID/분류/인원/소품/표시 상태를 앱 전용 설정에 저장합니다.
2. 촬영 직전에 모드·고정 프리셋 복사본·적용된 가이드 JSON을 CaptureChoice에 고정합니다.
3. CameraEngine이 AF/AE 상태를 확인하고 RAW 이미지와 CaptureResult를 시간으로 대응합니다.
4. DngCreator로 임시 DNG를 작성합니다. DngCrop이 DefaultCropOrigin/Size를 갱신한 뒤 실제 원본 파일로 확정합니다.
5. ShotProcessor가 RAW를 먼저 `Pictures/PressCamera`에 공개 저장하고 보도 클린 JPG를 개발합니다.
6. JPG는 회전 후 LandscapeSource를 통해 가로로 중앙 크롭한 3:2 정수 비율, 최대 품질(100)로 저장합니다. 리샘플링으로 비율을 맞추지 않습니다.
7. 가이드 데이터는 shot.json의 rawMeta.pressGuide에 기록되지만 픽셀에 합성하지 않습니다.

공용 사진첩에는 DNG/JPG가 각각 표시됩니다. RAW 센서 데이터에는 필터를 구워 넣지 않으며, 기본 크롭을 무시하는 뷰어에서는 원래 센서 비율이 나타날 수 있습니다. DNG 형식 참고: https://www.adobe.com/support/downloads/dng/dng_sdk.html

## 모드

메뉴는 세로 고정, 촬영 프레임·가이드 도형·JPG는 항상 가로 3:2입니다. DNG 기본 크롭은 orientation을 적용했을 때 가로가 되도록 센서 좌표에서 기록합니다. 프레임 상단의 모드/프리셋 안내 라벨은 표시하지 않습니다.

자동/프로는 모드 버튼을 누를 때마다 즉시 전환합니다. 자동은 매 실행의 기본이며 ISO=0, shutter=0, EV=0, focus=-1(AUTO)로 시작합니다. 프로에서 자동으로 전환하면 노출/수동 초점/ND를 해제합니다. 프로의 이전 다이얼 값은 같은 실행 안에서 보존됩니다. 카메라의 실제 지원 범위를 넘어서는 값은 엔진에서 제한합니다.

## 추천 상태

GuidePicker는 원래 선택의 복사본을 편집합니다. 사진 선택·인원·소품 변경은 화면에만 미리 보여줍니다. 적용 버튼에서 원본 선택과 설정을 갱신합니다. 바깥 터치/뒤로/닫기는 원래 선택으로 복원합니다. 메뉴가 열린 동안 볼륨키 촬영도 막습니다.

## 프리셋

JSON의 하나의 Recipe를 미리보기와 JPG 개발에 전달합니다. CPU/GPU 모두 색상/톤을 처리하며 detail·noise는 원본 측광 입력을 바꾸지 않습니다. 시험 이미지를 통한 GLSL 비교는 통과했지만 단말 ISP, 해상도 차이, 실제 센서 노이즈까지 동일함을 보장하지 않습니다.

## 빌드/버전

프로젝트명 PressCameraAndroid, applicationId com.hoonjay.presscamera, Java/R namespace com.hoonjay.filmroom입니다. 이름이 다른 것은 원본 엔진 소스의 패키지 이동을 피하기 위한 의도적인 구성입니다.

## 경고 오버레이

`ClippingRisk`는 미리보기 신호의 채널 경계를 판단합니다. `FilmShader`는 프리셋 이전 신호에서 영역 마스크를 만들고 화면 패스에서만 빨간 빗금을 그립니다. 측광·추적 패스의 zebra/riskSample 값은 0이며, 경고 집계용 소형 FBO는 미표시 신호만 읽습니다. CPU JPG 개발에는 빗금 코드가 없습니다.

경고 안내는 CameraOverlay가 프레임 아래 검은 상자에 테마색으로 표시합니다. 두 종류가 함께 발생하면 각각 표시합니다. 이는 실제 RAW 클리핑을 확정하는 센서 분석이 아닙니다.

## 가이드 외형

Selection의 opacity(0~100), color(7색 ARGB)를 copy/json/restore/normalize에서 함께 처리합니다. GuideRenderer는 0일 때 선·글자·그림자 모두 그리지 않습니다. 미니어처 선택 썸네일은 투명도와 무관하게 보입니다. 헤더의 이전 RAW+JPG/비율 표시는 투명도와 색상 조절로 교체했습니다.

## 삭제

목록 선택 상태는 ID 집합이며 화면 재생성 시 스크롤 위치를 보존합니다. 단일·다중 삭제는 확인 후 순차 실행하며 Stores.deleteShot를 공통으로 사용합니다. 공용 RAW/JPG와 앱 캐시를 지우고 tombstone을 기록하여 썸네일이나 늦은 작업이 사진을 되살리지 않게 합니다. 저장 중인 항목은 선택·삭제를 막고 실패 수를 분리 보고합니다.

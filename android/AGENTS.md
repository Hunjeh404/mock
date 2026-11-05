# 보도사진 카메라 — AI/개발 작업 지침

## 먼저 읽을 것

`app/src/main/assets/press-camera.manifest.json`, `docs/CONTENT_GUIDE.md`, `docs/ARCHITECTURE.md`, `docs/AI_HANDOFF.md`를 확인한다. 최신 소스와 첨부 APK의 버전이 다를 수 있으므로 Gradle과 제품 매니페스트의 버전을 먼저 대조한다.

## 제품 제약

- 기본 모드는 자동, 허용 모드는 자동/프로 두 개이다.
- 자동에서는 ISO·셔터·EV·초점 다이얼을 렌더링하거나 터치 대상으로 등록하지 않는다. 프로에서는 네 다이얼을 모두 표시한다. 조리개/ND는 실제 렌즈 기능에 따른다.
- 저장은 RAW(DNG)+JPG, JPG 품질 100, 휴대폰을 실제 가로로 들었을 때만 가로 3:2 촬영한다(메뉴는 세로). 세로로 들면 안내와 함께 일반/볼륨키/보조/타이머 촬영을 차단하고, 타이머 중 세로로 돌리면 취소한다. 세로 화면에 작은 가로 프레임을 고정하는 방식으로 되돌리지 않는다. JPG만 저장하는 대체 경로를 조용히 추가하지 않는다.
- 가이드 투명도는 상단 숫자 영역 좌우 드래그로 0~100 조정하며 지정한 7개 색상을 보존한다. 색상 팝업과 갤러리 선택/취소 버튼은 앱 테마를 따르고 글자가 잘리지 않게 한다. 0은 완전히 숨김이며 앱 재실행 후에도 유지한다.
- 명부/암부 빨간 빗금과 경고는 화면 전용이며 측광/추적/파일 저장에 합성하지 않는다. 경고는 센서 손실 단정이 아닌 촬영 신호 기반 위험 안내다.
- 삭제는 RAW/JPG 한 쌍과 앱 기록을 함께 처리한다. 저장 중인 사진은 삭제하지 않고, 실패한 작업을 성공으로 보고하지 않는다.
- 프리셋은 `press-clean` 하나만 자동 적용한다. 편집실, 프리셋 관리, 인물, 불꽃놀이, 별사진 모드를 복원하지 않는다.
- 구도 가이드는 화면 위에만 그린다. 원본 RAW나 JPG 픽셀에 가이드/문자를 합성하지 않는다.
- RAW 센서 샘플, 색상 보정, 노출, 타임스탬프 대응을 보존한다. 크롭은 DNG 기본 크롭 메타데이터로 처리한다.
- 앱 설치 ID는 `com.hoonjay.presscamera`이다. 원본 FilmRoom ID로 바꾸거나 원본 앱의 데이터에 접근하지 않는다.

## 데이터 수정 원칙

- 런타임의 유일한 분류 원본은 `press-guides/catalog.json`이다. JS 파일이나 Java 코드에 별도 분류 목록을 만들지 않는다.
- 이름·팁·키워드는 데이터에서 수정한다. 추천은 분류와 사진의 다대다 관계이며 `primary`와 `extra`를 구분한다.
- ID는 저장된 사용자 선택에 사용된다. 이름을 바꾸기 위해 ID를 바꾸지 않는다. 제거/ID 변경 시 `GuideCatalog.restore/normalize`의 이전 선택 마이그레이션을 검토한다.
- 새로운 사진 형태는 catalog + layouts index + 해당 도형 파일에 추가한다. 기존 도형 타입(rect/ellipse/path/text)으로 표현하면 Java 수정 없이 추가할 수 있다.
- `peopleOptions`의 모든 인원·소품 변형과 도형이 있어야 한다. 촬영 화면은 landscape 도형만 사용하며 기존 portrait 도형은 호환 자료로 유지한다.
- 호환되는 콘텐츠 변경 시 `catalogVersion`을 올린다. 데이터 구조가 호환되지 않으면 schemaVersion과 로더를 함께 변경한다.
- 도형은 0~1 정규 좌표다. 색상·선 스타일은 `GuideRenderer`에서 앱 공통 테마를 따른다.

## 코드와 생성물

- UI 진입점은 `MainActivity`; 촬영 HUD는 `CameraOverlay`; 선택 UI는 `guides/GuidePicker`다.
- Camera2 엔진에 분류/추천/UI 코드를 넣지 않는다.
- `build/`, `dist/`, 생성 R.java, 서명 키를 수정 대상으로 취급하지 않는다. 서명 키·SDK·로컬 경로를 Git에 올리지 않는다.
- 고정 프리셋의 노이즈/디테일 처리 변경은 CPU `Developer`와 GPU `FilmShader`를 함께 검토하고 `CameraPreview`의 중립 측광 패스가 영향을 받지 않게 한다.
- 버전은 `app/build.gradle`과 제품 매니페스트를 함께 갱신한다.

## 변경 후 검증

- 데이터/문구: `python3 scripts/validate-guides.py`, `bash scripts/test-guides.sh`
- 저장·RAW·비율·디테일: `bash scripts/test-press.sh`, `bash scripts/test-core.sh`
- 삭제/미디어 기록: `PRESS_JSON_TEST_JAR=... bash scripts/test-storage.sh`
- 초점 흐름: `bash scripts/test-capture-focus.sh`
- 프리셋/미리보기: `bash scripts/test-shader.sh`
- APK: `ANDROID_SDK_ROOT=... bash scripts/build-apk.sh`

관련 검사를 실행하고 결과와 제한을 보고한다. 실기기 촬영을 하지 않았다면 실사진 화질·초점·저장이 검증됐다고 말하지 않는다. 빌드 후 앱 이름, 패키지, 버전, 권한, 서명을 점검한다. 다음 사람이 바로 이어받도록 `docs/CHANGELOG.md`와 `docs/AI_HANDOFF.md`를 갱신한다.

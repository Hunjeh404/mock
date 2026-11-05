# 보도사진 카메라 1.1.1

FilmRoom Android 0.1.26의 RAW 촬영 엔진을 바탕으로 만든 보도사진 촬영 전용 앱입니다.

- 기본 자동 모드: ISO·셔터·EV·초점 다이얼을 숨기고 자동 제어
- 프로 모드: ISO·셔터·EV·초점 다이얼 표시, 렌즈 지원 시 조리개·ND 표시
- 보도 클린 프리셋 하나를 미리보기와 JPG 저장에 자동 적용
- RAW(DNG)+최대 품질 JPG 동시 저장, 휴대폰을 실제 가로로 들었을 때만 가로 3:2 촬영(메뉴는 세로)
- 보도자료 분류 복수 선택, 키워드 동시 추천, 사진 20종의 촬영 구도 가이드
- 가이드 선은 뷰파인더에만 표시하고 사진에는 합성하지 않음
- 명부·암부에 빨간 경고 빗금, 아래 검은 배경/테마색 안내문. 사진에는 미합성
- 모드 버튼을 누를 때마다 자동/프로 전환, 상단 투명도 좌우 드래그(0~100) 및 테마에 맞춘 7색 선택
- 사진 확인 화면 우측 상단 삭제, 목록의 복수 선택 삭제 지원
- 사진 확인·RAW+JPG 공유 지원, 편집실·프리셋 관리·인물·별사진·불꽃놀이 모드는 제거

기존 FilmRoom과 별도 앱으로 설치됩니다. 앱 ID는 `com.hoonjay.presscamera`이며 Android 11(API 30) 이상, Camera2 RAW 촬영을 지원하는 후면 렌즈가 필요합니다. 별도 앱이므로 기존 FilmRoom의 사진·설정을 읽거나 변경하지 않습니다.

## 자주 수정하는 파일

| 변경할 내용 | 파일 |
|---|---|
| 보도자료 분류, 이름, 검색 키워드, 추천·보완 관계, 촬영 안내 | `app/src/main/assets/press-guides/catalog.json` |
| 특정 사진 구도의 도형·인원·가로/세로 배치 | `app/src/main/assets/press-guides/layouts/<guide-id>.json` |
| 도형 파일 목록 | `app/src/main/assets/press-guides/layouts/index.json` |
| 보도 클린 프리셋의 색·노이즈·디테일 값 | `app/src/main/assets/press-guides/press-clean.json` |
| 제품 정보·정책·콘텐츠 파일 위치 | `app/src/main/assets/press-camera.manifest.json` |

이 JSON 파일들은 APK에 내장됩니다. 수정 후 APK를 다시 빌드해 배포해야 하며, 원격에서 문구를 바꾸는 관리 서버나 앱 내 편집 기능은 포함하지 않습니다.

## 유지보수 문서

1. `AGENTS.md`: AI·개발자가 따라야 하는 제품 제약과 검증 명령
2. `docs/CONTENT_GUIDE.md`: 분류·추천·안내·구도 추가/수정 예제
3. `docs/ARCHITECTURE.md`: 촬영·RAW·JPG·미리보기·가이드 연결 구조
4. `docs/AI_HANDOFF.md`: 다음 작업자가 먼저 확인할 내용과 실기기 검증 목록
5. `docs/CHANGELOG.md`: 제품 변경 이력

`press-camera.manifest.json`은 제품 설정 매니페스트이며 Android 시스템의 `AndroidManifest.xml`과 역할이 다릅니다. `AGENTS.md`는 작업 지침입니다.

## 빌드

JDK 17과 Android SDK Platform 35, Build Tools 35.0.0이 필요합니다.

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
bash scripts/build-apk.sh
```

결과: `dist/PressCamera-Android-1.1.1.apk`

빌드 스크립트는 앱 ID·버전·namespace를 `app/build.gradle`에서 읽습니다. 버전을 바꿀 때 `app/build.gradle`과 제품 매니페스트의 `product.version`을 함께 갱신하세요. 개발용 서명 키는 `signing/debug.keystore`에 있으며 Git에는 올리지 않습니다. 같은 앱을 업데이트할 때 동일한 서명 키를 사용해야 합니다. `PRESS_SIGNING_KEYSTORE`로 다른 위치의 개발 키를 지정할 수 있습니다. Play 배포용 릴리스 키 관리는 별도입니다.

Android Studio에서는 이 폴더를 열고 앱 모듈을 빌드할 수 있습니다. Gradle의 `namespace`는 원본 Java 패키지와 리소스 참조를 보존하기 위해 `com.hoonjay.filmroom`을 유지합니다. 실제 설치 ID와 표시 이름은 새 앱으로 분리되어 있습니다.

## 검증

```bash
bash scripts/test-press.sh
bash scripts/test-guides.sh
bash scripts/test-core.sh
bash scripts/test-storage.sh
bash scripts/test-capture-focus.sh
bash scripts/test-shader.sh
```

`test-guides.sh`의 첫 실행은 호스트 테스트용 `org.json` JAR를 Maven Central에서 받습니다. 이미 다운로드한 파일은 `PRESS_JSON_TEST_JAR`로 지정할 수 있습니다. APK 런타임은 Android 내장 JSON API를 사용하며 외부 의존성이나 인터넷 권한이 없습니다.

`test-shader.sh`는 Mesa EGL/GLES가 설치된 환경에서 실제 GLSL을 컴파일·렌더링해 CPU와 비교합니다. 단말 ISP나 실제 카메라를 흉내 내는 검사는 아닙니다.

## RAW와 비율

JPG는 회전을 적용한 뒤 리샘플링 없이 중앙을 잘라 항상 가로 3:2 정수 비율로 저장합니다. DNG는 모든 센서 샘플·색상 보정·노출 메타데이터를 보존하고 `DefaultCropOrigin`/`DefaultCropSize`만 같은 3:2 구도로 설정합니다. 따라서 기본 크롭을 무시하는 RAW 뷰어에서는 원래 센서 비율이 보일 수 있습니다. RAW에는 보도 클린 색상 프리셋을 구워 넣지 않습니다.

사진은 시스템 사진첩의 `Pictures/PressCamera`에 `PRESS_<timestamp>_RAW.dng`, `PRESS_<timestamp>_JPG.jpg` 한 쌍으로 저장됩니다. JPG 처리 실패 시 먼저 확보한 RAW는 보존되며 앱을 다시 실행하면 미완료 작업을 복구합니다.

## 검증 범위

소스 컴파일·APK 정렬/서명, 분류와 추천, 고정 비율, DNG 센서 데이터 보존, 초점 대기 정책, 실제 GLSL의 시험 이미지 렌더링을 확인했습니다. 실제 휴대폰에서 AF·AE, 렌즈별 RAW 스트림, 플래시, 회전, 팝업 키보드 높이, 장시간 연속 촬영과 실사진 화질은 별도 확인이 필요합니다.

## 명부·암부 경고

보정 프리셋이 검은색을 밝히거나 흰색을 압축하기 전의 미리보기 촬영 신호를 검사합니다. R/G/B 중 하나가 250/255 이상이면 명부, 모든 채널이 5/255 이하이면 암부 위험으로 표시합니다. 안내 문구는 제품 매니페스트의 warnings에서 바꿀 수 있습니다. 실제 RAW 센서 데이터 손실을 단정하는 기능은 아니며, 미리보기 처리와 실제 센서의 차이가 있습니다. 가이드 투명도·색상을 바꿔도 경고 빗금은 빨간색입니다.

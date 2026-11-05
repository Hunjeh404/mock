# 보도사진 카메라

기존 카메라 엔진에 보도사진 촬영 가이드를 통합한 Android 앱입니다.

- 설치 APK는 사용자에게 별도로 전달합니다. 이 저장소에는 현재 버전의 소스와 문서를 제공합니다.
- [Android 소스와 빌드 안내](android/README.md)
- [AI 작업 규칙](android/AGENTS.md)
- [분류·추천·안내·구도 수정 가이드](android/docs/CONTENT_GUIDE.md)
- [제품 설정 매니페스트](android/app/src/main/assets/press-camera.manifest.json)
- [구조 안내](android/docs/ARCHITECTURE.md)
- [다음 AI를 위한 인계](android/docs/AI_HANDOFF.md)
- [기존 웹 선택 UI 시안](https://hunjeh404.github.io/mock/)

자동/프로 탭 전환, 명부·암부 빨간 빗금, 가로 촬영 고정, 가이드 투명도/7색, 사진 단일·다중 삭제와 분류·키워드 추천을 제공합니다. 편집 화면과 프리셋 관리, 인물·불꽃놀이·별사진 모드는 없습니다.

앞으로 Android 가이드 콘텐츠는 android/app/src/main/assets/press-guides/를 수정하세요. 루트 JS 파일은 이전 웹 선택 UI의 시안입니다. 웹 시안은 카메라 앱 자체를 실행하는 페이지가 아니며, Android 앱의 현재 콘텐츠 원본은 assets JSON입니다.

서명 키는 공개 저장소에 포함하지 않습니다. 실기기 촬영 검증 범위는 인계 문서에서 확인하세요.

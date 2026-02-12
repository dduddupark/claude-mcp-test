## GitHub Issue 등록 가이드

본 문서는 작성된 "🔧 코드 리팩토링 제안" Issue를 GitHub에 등록하는 방법을 설명합니다.

---

## 📋 Issue 정보

**제목**: 🔧 코드 리팩토링 제안

**내용**: GITHUB_ISSUE.md 참조

**레이블**:
- `bug` - 빌드 실패
- `enhancement` - 코드 개선
- `android` - Android 플랫폼
- `compose` - Jetpack Compose 관련
- `build` - 빌드 관련

**우선순위**: High (🔴 심각)

---

## 🚀 등록 방법 (2가지)

### 방법 1: 웹 UI를 통한 등록 (권장)

1. **GitHub 리포지토리 접속**
   ```
   https://github.com/dduddupark/claude-mcp-test
   ```

2. **Issues 탭 클릭**
   - 상단 메뉴에서 "Issues" 선택

3. **"New Issue" 버튼 클릭**

4. **제목 입력**
   ```
   🔧 코드 리팩토링 제안
   ```

5. **본문 작성**
   - GITHUB_ISSUE.md의 내용을 복사하여 붙여넣기

6. **레이블 추가**
   - "Labels"에서 다음 선택:
     - `bug`
     - `enhancement`
     - `android`
     - `compose` (또는 직접 생성)
     - `build`

7. **"Submit new issue" 클릭**

---

### 방법 2: CLI를 통한 등록 (GitHub CLI)

**GitHub CLI 설치** (이미 설치된 경우 건너뛰기):
```bash
# macOS
brew install gh

# Windows
choco install gh

# Linux
sudo apt-get install gh
```

**로그인**:
```bash
gh auth login
```

**Issue 생성**:
```bash
cd /Users/yanadoo/Documents/test-folder/claude-mcp-test

gh issue create \
  --title "🔧 코드 리팩토링 제안" \
  --body "$(cat GITHUB_ISSUE.md)" \
  --label "bug,enhancement,android,compose,build"
```

---

## 📝 Issue 등록 후 추천 액션

### 1️⃣ Discussions 또는 Comment 추가

```
현재 빌드 실패 원인을 분석하여 위 이슈를 생성했습니다.

**빌드 실패의 주요 원인**:
1. Jetpack Compose 버전 불일치 (1.5.8 vs 2024.01.00)
2. AppCompat 의존성 누락
3. Compose + AppCompat 아키텍처 혼동

**빠른 해결 방법**:
- `app/build.gradle`에서 `kotlinCompilerExtensionVersion` → 1.5.10으로 변경
- AppCompat 의존성 추가: `androidx.appcompat:appcompat:1.6.1`
- AndroidManifest.xml에서 AppCompat 테마 제거

자세한 내용은 위 이슈를 참조해주세요.
```

### 2️⃣ Pull Request 생성

수정 사항을 적용한 후 PR을 생성합니다:

```bash
# 새 브랜치 생성
git checkout -b fix/refactor-build-issues

# 파일 수정 후
git add app/build.gradle AndroidManifest.xml

# 커밋
git commit -m "fix: Resolve build issues and improve code quality

- Fix: Jetpack Compose version mismatch (1.5.8 -> 1.5.10)
- Add: Missing AppCompat dependency
- Refactor: Remove AppCompat theme from Compose-only project
- Improve: Add BuildConfig for environment-specific ad IDs
- Enhance: Better error handling and logging
- Fix: Main thread safety for ad display

Closes #<issue-number>"

# PR 생성
git push origin fix/refactor-build-issues
gh pr create \
  --title "fix: Resolve build issues and improve code quality" \
  --body "Fixes #<issue-number>" \
  --label "bug,enhancement"
```

### 3️⃣ Milestone 할당 (선택)

- Version 1.1.0 또는 다음 릴리스 마일스톤 할당

### 4️⃣ Project 연결 (선택)

- 프로젝트 관리 보드가 있다면 이슈 추가

---

## 📊 예상 타임라인

| 단계 | 시간 |
|------|-----|
| Issue 등록 | 5분 |
| Issue 리뷰 및 논의 | 1-2일 |
| 코드 수정 및 PR 생성 | 1시간 |
| PR 리뷰 | 1-2일 |
| Merge | 즉시 또는 논의 후 |

---

## ✅ 체크리스트

등록 전에 다음을 확인하세요:

- [ ] GitHub 계정이 로그인되어 있는가?
- [ ] 리포지토리에 쓰기 권한이 있는가?
- [ ] Issue 제목이 명확한가?
- [ ] 본문 내용이 완전한가?
- [ ] 레이블을 올바르게 선택했는가?
- [ ] 기존 Issue와 중복이 없는가?

---

## 💡 Tip

1. **Issue 번호 확인**
   - 등록 후 URL에서 Issue 번호 확인
   - 예: `https://github.com/dduddupark/claude-mcp-test/issues/1`

2. **Discussion 활성화**
   - Settings → Features → Discussions 활성화 시 더 깊이 있는 토론 가능

3. **Templates 사용**
   - `.github/ISSUE_TEMPLATE/` 디렉토리에 템플릿 추가 시 자동 제공

4. **Automated Checks**
   - GitHub Actions로 자동 검사 설정 가능 (CI/CD)

---

## 🔗 생성된 파일

본 프로젝트에 생성된 관련 파일들:

1. **GITHUB_ISSUE.md** - GitHub Issue 본문 (복사-붙여넣기용)
2. **REFACTORING_PROPOSAL.md** - 상세 리팩토링 제안
3. **GITHUB_ISSUE_GUIDE.md** - 본 가이드 문서

---

**작성자**: 자동 생성  
**생성일**: 2026-02-12  
**버전**: 1.0

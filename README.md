# Gitddo Backend

GitHub 저장소를 분석해 포트폴리오 완성도, 개선 로드맵, 예상 면접 질문을 제공하는
서비스의 Spring Boot 백엔드입니다.

## 기술 구성

- Java 21
- Spring Boot 4.1
- Gradle
- Spring Data JPA
- Spring Security OAuth2 Login
- PostgreSQL 17
- Flyway
- Docker Compose
- Testcontainers

## 로컬 실행

요구사항:

- Java 21
- Docker와 Docker Compose

## GitHub OAuth App 등록

1. GitHub에서 `Settings → Developer settings → OAuth Apps → New OAuth App`으로 이동합니다.
2. 개발용 앱에 다음 값을 입력합니다.
   - Application name: `Gitddo Local`
   - Homepage URL: `http://localhost:8080`
   - Authorization callback URL: `http://localhost:8080/login/oauth2/code/github`
3. 앱을 생성하고 Client Secret을 발급합니다.
4. `.env.example`을 `.env`로 복사합니다.
5. 발급받은 값을 `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`에 입력합니다.

`.env`는 Git에 포함되지 않습니다. Client Secret을 코드, README, 로그에 기록하지
마세요. 개발용과 운영용 OAuth App도 분리하는 것을 권장합니다.

PostgreSQL을 실행합니다.

```bash
docker compose up -d
```

애플리케이션을 실행합니다.

```bash
./gradlew bootRun
```

상태 확인:

```bash
curl http://localhost:8080/actuator/health
```

브라우저에서 다음 주소를 열어 GitHub 로그인을 시작합니다.

```text
http://localhost:8080/oauth2/authorization/github
```

로그인에 성공하면 현재 사용자 API로 이동하며 다음 형태의 응답을 확인할 수 있습니다.

```json
{
  "githubId": 123456,
  "login": "github-login",
  "avatarUrl": "https://avatars.githubusercontent.com/u/123456"
}
```

`read:user`와 조직 멤버십 확인을 위한 `read:org` scope를 요청합니다. private 저장소
접근 권한인 `repo` scope는 요청하지 않습니다.

PostgreSQL을 종료하려면 다음 명령을 사용합니다.

```bash
docker compose down
```

데이터까지 제거하려면 `docker compose down -v`를 사용합니다.

## 환경 변수

기본 개발용 설정만으로 바로 실행할 수 있습니다. 값을 변경하려면 `.env.example`을
`.env`로 복사한 뒤 수정합니다.

| 변수 | 기본값 |
| --- | --- |
| `POSTGRES_DB` | `gitddo` |
| `POSTGRES_USER` | `gitddo` |
| `POSTGRES_PASSWORD` | `gitddo` |
| `POSTGRES_PORT` | `5432` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/gitddo` |
| `DB_USERNAME` | `gitddo` |
| `DB_PASSWORD` | `gitddo` |
| `GITHUB_CLIENT_ID` | GitHub OAuth App에서 발급 |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App에서 발급 |
| `SESSION_COOKIE_SECURE` | 로컬 `false`, HTTPS 운영 환경 `true` |
| `SPRINGDOC_ENABLED` | 로컬 `true`, 운영 환경에서는 필요에 따라 `false` |

운영 환경에서는 기본 비밀번호를 사용하지 않습니다.

## Docker PostgreSQL 직접 확인

PostgreSQL 컨테이너 상태를 확인합니다.

```bash
docker compose ps
```

컨테이너 안의 `psql`로 접속합니다.

```bash
docker compose exec postgres psql -U gitddo -d gitddo
```

접속한 뒤 자주 사용하는 명령:

```text
\dt
\d github_users
SELECT github_id, login, created_at, updated_at FROM github_users;
\q
```

한 번의 명령으로 사용자 목록만 확인할 수도 있습니다.

```bash
docker compose exec postgres psql -U gitddo -d gitddo \
  -c "SELECT github_id, login, created_at, updated_at FROM github_users;"
```

Docker Desktop의 `gitddo-postgres` 컨테이너에서 Exec 탭을 사용해 동일한 `psql`
명령을 실행할 수도 있습니다.

## OpenAPI와 Swagger UI

서버 실행 후 다음 주소에서 API 문서를 확인합니다.

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON은 다음 주소에서 제공합니다.

```text
http://localhost:8080/v3/api-docs
```

인증이 필요한 API를 실행하려면 같은 브라우저에서 먼저 GitHub 로그인을 완료해야
합니다. 로그인 세션은 HttpOnly `JSESSIONID` 쿠키로 전달되므로 Swagger UI에 토큰을
직접 입력하지 않습니다. `POST`, `PUT`, `DELETE` 요청은 먼저 `GET /api/v1/csrf`를
실행해 `XSRF-TOKEN` 쿠키를 발급받아야 합니다. Swagger UI는 쿠키 값을
`X-XSRF-TOKEN` 헤더로 자동 전송합니다. 프런트엔드도 같은 쿠키와 헤더 규칙을 사용합니다.

## GitHub public 저장소 조회 및 검색

GitHub 로그인 후 다음 API로 사용자가 소유하거나 참여한 public 저장소를 모두 조회합니다.

```text
GET /api/v1/github/repositories
```

서버가 GitHub API의 페이지당 최대 크기인 100개씩 조회하고, 다음 페이지가 있으면 끝까지
가져온 뒤 하나의 응답으로 합쳐 반환합니다. `owner`, `collaborator`,
`organization_member` 관계를 조회하므로 개인 소유 저장소뿐 아니라 협업한 저장소와
조직 구성원으로 참여한 저장소도 포함합니다. fork와 archived 저장소는 응답에 포함하며
각각의 필드로 구분합니다.

응답에는 전체 개수, GitHub API 남은 요청 횟수, 전체 저장소 목록이 포함됩니다.
조회한 목록은 DB에 동기화되며, 소유자(개인 또는 조직)나 프로젝트 이름으로 검색할 수
있습니다. 검색 조건은 대소문자를 구분하지 않는 부분 일치이며 각각 생략할 수 있습니다.

```text
GET /api/v1/github/repositories/search?owner=git-ddo&name=backend
```

## 코칭 포트폴리오

동기화된 저장소를 최대 5개까지 선택하고 저장소별 역할, 참여 수준, 대표 역할과 기여
내용을 입력해 코칭 포트폴리오를 구성합니다. 저장소 없이 초안 포트폴리오를 만들 수
있으며, 평가를 요청할 때는 저장소가 1개 이상 필요합니다. 평가 분야는 복수 선택할 수
있으며 평가 목적은 하나를 선택합니다.

```text
POST   /api/v1/portfolios
GET    /api/v1/portfolios
GET    /api/v1/portfolios/{portfolioId}
PUT    /api/v1/portfolios/{portfolioId}
DELETE /api/v1/portfolios/{portfolioId}

POST   /api/v1/portfolios/{portfolioId}/repositories
PUT    /api/v1/portfolios/{portfolioId}/repositories/{repositoryId}
DELETE /api/v1/portfolios/{portfolioId}/repositories/{repositoryId}?version={version}

POST   /api/v1/portfolios/{portfolioId}/evaluations
GET    /api/v1/portfolios/{portfolioId}/evaluations/{analysisId}
```

수정 요청에는 마지막 조회 응답의 `version`을 전달합니다. 다른 요청이 먼저 수정했다면
`409 Conflict`를 반환합니다. 평가 요청 계약은 현재 포트폴리오 입력을 JSON 스냅샷으로
보존하므로, 포트폴리오를 수정하고 재평가해도 이전 평가 입력과 결과를 유지할 수 있습니다.
저장소 조회·검색 결과의 GitHub 저장소 ID를 저장소 추가 API에 전달하며, 저장소는 중복 없이
최대 5개까지 추가할 수 있습니다.

평가 요청은 `202 Accepted`와 외부 식별자인 `analysisId`를 반환합니다. 백그라운드 작업이
저장소별 commit SHA를 고정하고 언어, 파일 트리, README, 빌드·CI·컨테이너·API 문서를
선별해 P0 Evidence로 저장한 뒤, 로그인 사용자의 커밋·PR을 P1 Evidence로 이어서 수집합니다.
P1은 최근 커밋을 그대로 쓰지 않고, lockfile 등 노이즈를 제외한 소스 변경량과
domain/service/controller 경로, feat/refactor/arch 메시지에 가중치를 둬 핵심 활동만 고릅니다.
P2는 그 커밋/PR이 가리키는 소스 파일 전체가 아니라, 클래스·메서드 주변의 짧은 코드 조각만
`CODE_EVIDENCE`로 붙입니다. 이어서 `ANALYZING`으로 바꾸고
`POST /internal/v1/portfolio-reports`로 리포트를 받습니다.
응답의 `analysisId`, Evidence ID, Snapshot SHA를 검증한 뒤에만 DB에 저장합니다.
상태 조회에서 `SUCCEEDED`가 반환되면 `report`로 코칭 리포트를 확인할 수 있습니다.

지금은 `gitddo.ai.mode=mock`이 기본값이라 실제 AI 서버 없이 같은 흐름을 검증합니다.
AI 서버를 붙일 때는 `GITDDO_AI_MODE=http`, `GITDDO_AI_BASE_URL`에 ngrok 등 임시 주소를 넣습니다.
요청/응답 계약은 `docs/contracts/`와 `docs/contracts/examples/`를 기준으로 맞춥니다.

```text
REQUESTED → COLLECTING → EVIDENCE_READY → ANALYZING → SUCCEEDED
                                      ↘ FAILED
```

## 데이터베이스 관리

스키마는 Hibernate 자동 생성이 아니라 Flyway migration으로 관리합니다.

- migration: `src/main/resources/db/migration`
- JPA 설정: `ddl-auto: validate`
- 최초 테이블: `github_users`

## 패키지 방향

기능 중심의 모듈형 모놀리스 구조를 사용합니다.

```text
com.gitddo
├── member       # 사용자 계정과 GitHub 사용자 정보
├── github       # GitHub API 연동과 저장소 동기화
├── analysis     # 코드 분석 작업과 결과
├── portfolio    # 점수, 로드맵, 예상 질문
└── common       # 공통 예외, 응답, 설정
```

필요한 기능이 생길 때 패키지를 추가하며, 비어 있는 계층이나 클래스를 미리 만들지
않습니다.

## 테스트

```bash
./gradlew test
```

통합 테스트는 Testcontainers가 임시 PostgreSQL 컨테이너를 실행해 실제 migration과
JPA 저장을 검증합니다.

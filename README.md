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

이 시점에는 `read:user` scope만 요청하며 private 저장소 권한은 요청하지 않습니다.

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
직접 입력하지 않습니다.

## GitHub public 저장소 조회

GitHub 로그인 후 다음 API로 사용자가 소유한 public 저장소를 모두 조회합니다.

```text
GET /api/v1/github/repositories
```

서버가 GitHub API의 페이지당 최대 크기인 100개씩 조회하고, 다음 페이지가 있으면 끝까지
가져온 뒤 하나의 응답으로 합쳐 반환합니다. 현재는 `affiliation=owner`를 사용하므로 다른
사용자의 저장소에 collaborator로 참여한 경우나 조직 소유 저장소는 포함하지 않습니다.
fork와 archived 저장소는 응답에 포함하며 각각의 필드로 구분합니다.

응답에는 전체 개수, GitHub API 남은 요청 횟수, 전체 저장소 목록이 포함됩니다.
이 단계에서는 목록을 DB에 저장하지 않습니다.

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

# Tikkle 프로젝트 코딩 가이드라인 (Rules)

이 문서는 Tikkle 프로젝트에서 코드를 작성하거나 수정할 때 반드시 지켜야 할 아키텍처 및 구현 규칙입니다. AI 어시스턴트는 코드를 생성할 때 본 문서의 규칙을 최우선으로 적용해야 합니다.

## 1. Architecture & Layer Rules

본 프로젝트는 Controller - Service - Repository 계층형 아키텍처를 따릅니다.

- **Controller Layer**
  - 클라이언트의 HTTP 요청을 받고 응답을 반환하는 역할만 수행합니다.
  - 비즈니스 로직은 포함하지 않으며, 모든 핵심 로직은 Service 계층으로 위임합니다.
  - DTO 변환 및 유효성 검사(`@Valid`)를 수행합니다.
- **Service Layer**
  - 핵심 비즈니스 로직을 담당합니다.
  - 트랜잭션 관리를 위해 클래스 또는 메서드 레벨에 `@Transactional`을 명시합니다. (조회 전용 메서드에는 `@Transactional(readOnly = true)` 사용)
- **Repository Layer**
  - 데이터베이스 접근을 담당합니다. Spring Data JPA 인터페이스를 활용합니다.
  - 복잡한 쿼리가 필요한 경우 `@Query`(JPQL)를 사용합니다. (QueryDSL은 도입하지 않았습니다.)

## 2. 신규 도메인 개발 가이드

새로운 도메인(예: `Payment`, `Event`)을 개발할 때는 기존 `user` 도메인의 구조를 따라야 합니다. 다음은 신규 도메인 개발 시 필수적으로 생성 및 수정해야 할 파일 목록입니다.

1.  **Swagger 인터페이스 생성**:
    -   `{Domain}Swagger.java` 파일을 해당 도메인 패키지 내 `swagger` 패키지에 생성합니다. (예: `payment.swagger.PaymentSwagger`)
    -   이 인터페이스는 API의 명세와 문서화를 담당하며, Controller가 이를 구현해야 합니다.

2.  **ErrorCode 추가**:
    -   새로운 도메인에서 발생할 수 있는 예외 상황을 `global.exception.ErrorCode` Enum에 추가합니다.
    -   도메인별로 주석을 달아 그룹화하고, 식별 코드(예: `PAYMENT-001`)를 부여합니다.

    ```java
    // 8. 결제 (Payment)
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT-001", "결제 정보를 찾을 수 없습니다."),
    ```

3.  **필요시 CustomException 클래스 생성**:
    -   도메인에 특화된 예외 정보(예: `orderId`)를 담아야 하는 경우, `CustomException`을 상속받는 새로운 예외 클래스를 생성할 수 있습니다. (예: `PaymentFailedException.java`)
    -   이 경우 `GlobalExceptionHandler`에도 해당 예외를 처리하는 핸들러 메서드를 추가해야 합니다.

4.  **패키지 구조 준수**:
    -   기본적으로 `controller`, `service`, `repository`, `entity`, `dto` 패키지를 생성하고, `dto` 내부에 `request`, `response` 패키지를 분리합니다.

AI 어시스턴트는 새로운 도메인에 대한 기능 구현을 요청받았을 때, 위 규칙에 따라 필요한 파일들을 먼저 생성하거나 수정하는 계획을 세우고 작업을 진행해야 합니다.

## 3. API Response Wrapper

모든 API 응답은 `ResponseEntity`를 사용하지 않고, `com.tikkle.global.response.ApiResponse<T>` 객체를 직접 반환하여 통일된 포맷을 유지합니다. HTTP 상태 코드는 기본적으로 200 OK가 적용되며, 201 Created 등 다른 상태 코드가 필요한 경우 메서드에 `@ResponseStatus` 어노테이션을 사용합니다.

### 성공 응답 (Controller 예시)
```java
// 데이터가 있는 경우 (기본 200 OK)
@GetMapping("/{id}")
public ApiResponse<UserResponse> getUser(@PathVariable Long id) {
    return ApiResponse.success(userService.getUser(id));
}

// 상태 코드 변경 및 데이터가 있는 경우 (예: 201 Created)
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<UserResponse> createUser(@RequestBody @Valid CreateUserRequest request) {
    return ApiResponse.success(userService.createUser(request));
}

// 데이터가 없는 경우
@DeleteMapping("/{id}")
public ApiResponse<?> withdrawUser(@PathVariable Long id) {
    userService.withdrawUser(id);
    return ApiResponse.successWithNoData();
}
```

## 4. Exception Handling

모든 비즈니스 예외는 `com.tikkle.global.exception.CustomException`을 상속받는 해당 도메인의 예외 클래스를 던져서 처리합니다. 예외 발생 시 `ErrorCode` Enum을 활용하여 의미를 명확히 합니다. 에러 발생 시 `GlobalExceptionHandler`가 일괄 포착하여 처리합니다.

### 예외 발생 및 처리 예시 (Service Layer)
```java
private User findActiveUserByEmail(String email) {
    return userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
            .orElseThrow(UserNotFoundException::new);
}
```

### ErrorCode 추가 가이드
새로운 에러가 필요한 경우 `ErrorCode.java`에 추가해야 합니다.
```java
// 에러 상태, 에러 코드, 에러 메시지 순으로 작성
USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "사용자를 찾을 수 없습니다."),
```

## 5. Entity & DTO Convention

### Entity 작성 규칙
- 무분별한 `@Setter` 사용을 엄격히 금지합니다.
- 엔티티의 상태 변경은 도메인 의미를 담은 비즈니스 메서드(예: `updateProfile()`, `withdraw()`)를 통해 수행합니다.
- 객체 생성은 안전성 확보를 위해 `@Builder`를 사용하며, 클래스 레벨이 아닌 생성자 레벨에 적용합니다.
- 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 제한합니다.

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    // 필드 생략...

    @Builder
    private User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    // 도메인 비즈니스 메서드
    public void update(String name) {
        if(name != null) this.name = name;
    }
}
```

### DTO 분리 규칙
- 클라이언트의 요청과 응답 객체는 철저히 분리해야 합니다 (`Request`, `Response` 접미사 사용).
- 패키지 분리 예시: `dto.request.CreateUserRequest`, `dto.response.UserResponse`
- 엔티티를 DTO로 변환할 때는 DTO 내부에 `from(Entity entity)` 같은 정적 팩토리 메서드를 구현하여 사용하는 것을 권장합니다.

## 6. Swagger / OpenAPI Documentation

컨트롤러 코드의 가독성을 높이기 위해 Swagger 어노테이션은 **별도의 Interface로 분리**하여 관리합니다. 실제 Controller 클래스는 해당 인터페이스를 `implements` 합니다.

### Swagger Interface 템플릿
```java
@Tag(name = "User", description = "유저 API")
public interface UserSwagger {

    @Operation(summary = "유저 조회", description = "ID로 유저 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{ \"code\": \"SUCCESS\", \"message\": \"요청에 성공했습니다.\", \"data\": { ... } }"
                    ))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "유저 찾을 수 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{ \"code\": \"USER-001\", \"message\": \"사용자를 찾을 수 없습니다.\" }"
                    )))
    })
    ApiResponse<UserResponse> getUser(@Parameter(description = "유저 ID") Long id);
}
```

## 7. 일반 코딩 스타일 및 네이밍
- **Naming**: 
  - Class/Interface: `PascalCase`
  - Method/Variable: `camelCase`
  - Constants(상수): `UPPER_SNAKE_CASE`
- **의존성 주입**: 생성자 주입을 사용하며, 롬복의 `@RequiredArgsConstructor`를 활용하여 코드를 간결하게 유지합니다. (`@Autowired` 사용 지양)
- **하드코딩된 패키지 경로(FQCN) 금지**: 예외 클래스나 다른 객체를 참조할 때 `com.tikkle.payment.exception.DuplicatePaymentException`처럼 패키지 경로를 하드코딩하지 않습니다. 반드시 파일 상단에 `import` 문을 추가하고 클래스 이름만 사용합니다.
- **로깅 (Logging)**: 로그 메시지는 **한국어**로 작성하며, 식별 및 가독성을 위해 `[클래스명] 행동/이벤트 내용 - 파라미터명: {}` 포맷을 준수합니다. (Controller는 로그 생략, Service/Interceptor 등 핵심 비즈니스 로직에만 추가하되 단순 조회는 생략)
- **주석 (Comments)**: Controller, Service, Filter, Interceptor 등 주요 로직 클래스와 메서드에는 **Javadoc(`/** ... */`)**을 필수로 사용합니다. (Entity, DTO, Repository, Swagger는 생략). 내부 로직 설명 시 장식 없는 깔끔한 인라인 주석(`//`)을 사용합니다.
- **식별자 및 네이밍 규칙**: 인증 객체에서 사용자 식별 시 `getUserId()`(Long)를 사용하며, Entity `@Table` 속성 등 테이블명은 대문자 스네이크 케이스로 작성합니다.

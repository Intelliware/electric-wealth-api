---
title: "Dependency Injection and Service Registration"
block: reverse-eng/api/internal/dependency-injection-and-service-registration
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 6. Dependency Injection and Service Registration

### 6.1 DI Framework

Spring Framework's application context, bootstrapped by `@SpringBootApplication` on
[ElectricWealthApplication.java](../../src/main/java/com/electricwealth/api/ElectricWealthApplication.java), which
component-scans `com.electricwealth.api.**`. `@ConfigurationPropertiesScan` registers `AppProperties`.

### 6.2 Registration Patterns

| Pattern | Used for | Example |
|---|---|---|
| Stereotype + constructor injection (single constructor, no `@Autowired`) | Controllers, repositories, filters, `ErrorResponses`, `RequiredRecordFieldsConverter` | `public GoalController(GoalRepository goals)` |
| `@Configuration` + `@Bean` methods | Infrastructure | `DatabaseConfig.dataSource`, `SecurityConfig.securityFilterChain`, `SecurityConfig.corsConfigurationSource`, `OpenApiConfig.openApi` / `apiErrorsCustomizer` / `errorEnvelopeSchema`, `JacksonConfig.javascriptStyleNumbers` |
| `WebMvcConfigurer` | MVC customisation | `WebConfig.addFormatters` registers `JsonEnums.CONVERTER_FACTORY` |
| `@ConfigurationProperties` record | Typed settings | `AppProperties` (`ew.*`) |
| Manual `new` inside a `@Bean` | A filter that must run **only** inside the security chain | `new BearerTokenFilter(sessions)` in `SecurityConfig` — deliberately not a `@Component` so Boot doesn't also register it as a servlet filter |
| Static utility classes (no DI) | Helpers | `JsonEnums`, `Rows`, `SqlValues`, `TraceIds`, `BearerTokens`, `Text`, `ApiParams`, `Labels` |

Beans supplied by Boot auto-configuration and injected here: `JdbcClient` (from `spring-boot-starter-jdbc`, over
the `HikariDataSource` bean), `JsonMapper` (Jackson 3, injected into `ErrorResponses`), `DataSourceProperties`,
the JDBC transaction manager behind `@Transactional`, `MockMvcTester` in tests.

### 6.3 Service Lifetimes

All application beans are **singletons** (Spring default; no `@Scope` anywhere in `src/main`). Consequences:

- Repositories and controllers are stateless apart from injected dependencies.
- `RateLimitFilter` holds an in-memory `ConcurrentHashMap<String, Bucket>` for the life of the process.
- `JsonEnums.VALUES` and `Rows.MAPPERS` are static `ConcurrentHashMap` caches shared across threads.
- Per-request state lives on the `HttpServletRequest` (trace id attribute), in the SLF4J `MDC` (`traceId`) and in the `SecurityContextHolder` (thread-local `AuthenticatedUser`).

### 6.4 Common Mistakes

- **Annotating `BearerTokenFilter` with `@Component`.** Boot would register it as a global servlet filter too, so it would run twice and outside the security chain.
- **Adding field injection.** Every class uses constructor injection; keep it that way for testability.
- **Adding a second `OncePerRequestFilter` without `@Order`.** `TraceFilter` (`HIGHEST_PRECEDENCE`) and `RateLimitFilter` (`HIGHEST_PRECEDENCE + 10`) rely on explicit ordering; an unordered filter runs after them with `LOWEST_PRECEDENCE`.
- **Creating a second `DataSource` bean.** `DatabaseConfig` builds the only one; a second bean would make `JdbcClient` auto-configuration ambiguous.

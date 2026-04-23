# 웹 프레임워크 프로그래밍 과제 보고서
## Spring MVC + JPA 상품 관리 시스템

---

## ① GitHub Repository URL

> (본인 GitHub Repository URL 기입)

---

## ② 아키텍처 다이어그램

### Spring MVC 4계층 흐름

```
┌─────────────────────────────────────────────────────────────────────┐
│                        클라이언트 (Browser)                          │
│                    HTTP Request / Response                           │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ HTTP GET/POST
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  DispatcherServlet (Front Controller)               │
│         모든 요청의 진입점. HandlerMapping으로 Controller 탐색       │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ URL 매핑 (@GetMapping / @PostMapping)
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     Controller Layer                                │
│  ┌─────────────────────┐      ┌──────────────────────────────────┐ │
│  │  ProductController  │      │       CategoryController         │ │
│  │  GET  /products     │      │  GET  /categories                │ │
│  │  POST /products/... │      │  POST /categories/create         │ │
│  └──────────┬──────────┘      └─────────────┬────────────────────┘ │
│             │ @RequestParam / @PathVariable  │                      │
│             │ @ModelAttribute / @Valid       │                      │
└─────────────┼──────────────────────────────-┼──────────────────────┘
              │ 비즈니스 로직 위임              │
              ▼                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       Service Layer                                 │
│  ┌─────────────────────┐      ┌──────────────────────────────────┐ │
│  │   ProductService    │      │        CategoryService           │ │
│  │  @Transactional     │      │  @Transactional                  │ │
│  │  비즈니스 규칙 적용  │      │  중복 방지 / 삭제 검증           │ │
│  └──────────┬──────────┘      └─────────────┬────────────────────┘ │
└─────────────┼───────────────────────────────┼──────────────────────┘
              │ 데이터 접근 위임               │
              ▼                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Repository Layer                               │
│  ┌─────────────────────┐      ┌──────────────────────────────────┐ │
│  │  ProductRepository  │      │      CategoryRepository          │ │
│  │  JPQL 쿼리 실행     │      │  JPQL 쿼리 실행                  │ │
│  │  EntityManager 사용 │      │  EntityManager 사용              │ │
│  └──────────┬──────────┘      └─────────────┬────────────────────┘ │
└─────────────┼───────────────────────────────┼──────────────────────┘
              │ persist / merge / remove / find│
              ▼                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│               JPA / Hibernate (영속성 컨텍스트)                      │
│                                                                     │
│   Entity 생명주기:  Transient → Managed → Detached → Removed        │
│   1차 캐시 (Persistence Context) · 더티 체킹 · 지연 로딩(LAZY)       │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ SQL (INSERT / SELECT / UPDATE / DELETE)
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       MySQL Database                                │
│          category · product · product_detail · tag · product_tag   │
└─────────────────────────────────────────────────────────────────────┘
```

### 요청 처리 흐름 예시 (상품 목록 조회)

```
GET /products?keyword=노트북&categoryId=1

  1. DispatcherServlet → ProductController.listProducts() 호출
  2. Controller: keyword, categoryId 파라미터 수신
  3. ProductService.searchByNameAndCategory("노트북", 1L) 호출
  4. ProductRepository JPQL 실행:
       SELECT p FROM Product p
       LEFT JOIN FETCH p.category
       WHERE p.name LIKE '%노트북%' AND p.category.id = 1
  5. Hibernate → MySQL SQL 변환 및 실행
  6. List<Product> 반환 → Model에 담음
  7. Thymeleaf: productList.html 렌더링 → HTTP Response 반환
```

### 주요 설정 클래스 구조

```
WebAppInitializer (web.xml 대체)
 ├── Root Context  → DbConfig
 │    ├── DataSource (MySQL 연결)
 │    ├── EntityManagerFactory (Hibernate)
 │    └── JpaTransactionManager (@Transactional 처리)
 └── Servlet Context → WebConfig
      ├── SpringResourceTemplateResolver
      ├── SpringTemplateEngine
      └── ThymeleafViewResolver
```

---

## ③ 추가 기능 설명

### 3-1. Category 관리 기능

#### 전체 엔드포인트

| HTTP Method | URL | 설명 |
|-------------|-----|------|
| GET | `/categories` | 카테고리 목록 |
| GET | `/categories/create` | 등록 폼 |
| POST | `/categories/create` | 등록 처리 |
| POST | `/categories/{id}/delete` | 삭제 처리 |

#### 핵심 코드 및 동작 원리

**① CategoryRepository — 이름 중복 검사 (JPQL)**

```java
// CategoryRepository.java
public Optional<Category> findByName(String name) {
    List<Category> result = em.createQuery(
            "SELECT c FROM Category c WHERE c.name = :name", Category.class)
            .setParameter("name", name)
            .getResultList();
    return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
}
```

힌트에 제시된 구조를 보면 `getResultList()`로 결과를 받은 뒤 비어있으면 `Optional.empty()`, 있으면 첫 번째 요소를 `Optional`로 감싸 반환하는 방식이다. `getSingleResult()`를 쓰면 결과가 없을 때 예외가 발생하기 때문에 이렇게 처리하면 Service 쪽에서 null 체크 없이 `ifPresent()` 같은 Optional API를 바로 사용할 수 있다.

**② CategoryService — 중복 방지 비즈니스 로직**

```java
// CategoryService.java
@Transactional
public Category createCategory(String name) {
    categoryRepository.findByName(name)
            .ifPresent(c -> { throw new DuplicateCategoryException(name); });
    return categoryRepository.save(new Category(name));
}
```

힌트 코드를 보면 `findByName()`으로 같은 이름이 이미 있으면 `ifPresent()`가 실행되어 `DuplicateCategoryException`을 던지고, 없으면 바로 저장하는 구조다. 중복 검사 같은 비즈니스 규칙은 Repository가 아닌 Service 계층에서 처리하도록 분리되어 있다. 이 예외는 Controller에서 `BindingResult.rejectValue()`로 받아 에러 페이지로 넘어가지 않고 폼 화면에 오류 메시지로 표시된다.

**③ CategoryService — 삭제 시 연결 상품 검증**

```java
// CategoryService.java
@Transactional
public void deleteCategory(Long id) {
    long count = categoryRepository.countProductsByCategoryId(id);
    if (count > 0) throw new IllegalStateException(
            "상품 " + count + "개가 연결되어 있어 삭제할 수 없습니다.");
    categoryRepository.delete(id);
}
```

힌트에 제시된 구조를 보면 삭제 전에 COUNT 쿼리로 연결된 상품 수를 먼저 확인하고, 1개 이상이면 예외를 던져 삭제를 막는다. Controller에서는 이 예외를 `RedirectAttributes.addFlashAttribute()`로 전달해서 리다이렉트 후 목록 화면에 오류 메시지가 한 번만 뜨도록 되어 있다.

**④ DuplicateCategoryException — 커스텀 예외**

```java
// exception/DuplicateCategoryException.java
public class DuplicateCategoryException extends RuntimeException {
    public DuplicateCategoryException(String name) {
        super("이미 존재하는 카테고리입니다: " + name);
    }
}
```

힌트에 제시된 구조로, `RuntimeException`을 상속하기 때문에 `@Transactional` 메서드 안에서 발생하면 자동으로 롤백된다. `GlobalExceptionHandler`에 등록하지 않고 Controller에서 직접 처리하는 방식으로 폼 오류 메시지로 연결된다.

---

### 3-2. 상품 검색 기능

#### 검색 조건 4가지 케이스

| keyword | categoryId | 호출 메서드 |
|---------|-----------|-----------|
| O | O | `searchByNameAndCategory(keyword, categoryId)` |
| O | X | `searchByName(keyword)` |
| X | O | `searchByCategory(categoryId)` |
| X | X | `getAllProducts()` |

#### 핵심 코드 및 동작 원리

**① ProductRepository — JPQL 검색 쿼리**

```java
// 이름 검색
public List<Product> findByNameContaining(String keyword) {
    return entityManager.createQuery(
                    "SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.name LIKE :keyword",
                    Product.class)
            .setParameter("keyword", "%" + keyword + "%")
            .getResultList();
}

// 카테고리 필터
public List<Product> findByCategory(Long categoryId) {
    return entityManager.createQuery(
                    "SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.category.id = :cid",
                    Product.class)
            .setParameter("cid", categoryId)
            .getResultList();
}

// 이름 + 카테고리 동시 필터
public List<Product> findByNameContainingAndCategory(String keyword, Long categoryId) {
    return entityManager.createQuery(
                    "SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.name LIKE :keyword AND p.category.id = :cid",
                    Product.class)
            .setParameter("keyword", "%" + keyword + "%")
            .setParameter("cid", categoryId)
            .getResultList();
}
```

힌트에는 `findByNameContaining()`과 `findByCategoryId()` 두 개만 제시되어 있었다. 실제로 구현해보니 검색 결과가 여러 개여야 하는데 하나씩밖에 안 나오는 문제가 생겼다. 원인을 찾아보니 `Product.category`가 `FetchType.LAZY`라서 트랜잭션이 끝난 후 Thymeleaf에서 `product.category.name`에 접근하면 `LazyInitializationException`이 발생하는 것이었다. `findAll()`에는 `LEFT JOIN FETCH p.category`가 있었는데 검색 메서드에는 빠져 있어서 직접 추가했다. 또한 키워드와 카테고리를 동시에 필터링하는 `findByNameContainingAndCategory()`는 힌트에 없어서 직접 작성했다. LIKE 검색에서 파라미터 앞뒤로 `%`를 붙이면 키워드가 상품명 어디에 있어도 매칭된다.

**② ProductController — 검색 조건 분기**

```java
// ProductController.java
@GetMapping
public String listProducts(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long categoryId,
        Model model) {

    boolean hasKeyword = keyword != null && !keyword.isBlank();
    boolean hasCategory = categoryId != null;

    if (hasKeyword && hasCategory) {
        products = productService.searchByNameAndCategory(keyword, categoryId);
    } else if (hasKeyword) {
        products = productService.searchByName(keyword);
    } else if (hasCategory) {
        products = productService.searchByCategory(categoryId);
    } else {
        products = productService.getAllProducts();
    }
    ...
}
```

힌트에는 keyword 또는 categoryId 둘 중 하나만 처리하는 if-else 구조가 제시되어 있었다. 그대로 쓰니 키워드 검색 후 카테고리를 선택해도 필터링이 반영되지 않는 문제가 있었다. keyword와 categoryId를 동시에 받는 케이스를 추가해서 4가지 조합으로 분기 처리했다. `@RequestParam(required = false)`로 선언하면 파라미터가 없을 때 null이 들어오고, 폼 전송 시 keyword가 빈 문자열로 넘어올 수도 있어서 `isBlank()`로 함께 체크했다.

---

## ⑤ 학습 소감 및 어려웠던 점

검색 기능을 구현한 후 검색 결과가 여러 개여야 함에도 하나씩밖에 표시되지 않는 문제가 발생했다. 원인을 분석해보니 `Product.category`가 `FetchType.LAZY`로 선언되어 있어, 검색 쿼리 실행 후 트랜잭션이 종료되면 Thymeleaf 뷰에서 `product.category.name`에 접근할 때 `LazyInitializationException`이 발생하는 것이었다. `findAll()`에는 `LEFT JOIN FETCH p.category`가 있었지만 검색 메서드에는 누락되어 있었고, 이를 추가함으로써 해결할 수 있었다. 또한 키워드 검색과 카테고리 필터를 동시에 적용하려 했을 때 기존 if-else 구조에서는 keyword가 있으면 categoryId를 완전히 무시하는 문제가 있었다. 두 조건의 조합 케이스(둘 다 있음 / keyword만 / categoryId만 / 둘 다 없음)를 별도로 분기 처리하고, Repository에 keyword와 categoryId를 모두 받는 JPQL 쿼리를 추가하여 해결했다.

---

## ⑥ 자기평가 체크리스트

| 배점 | 채점 항목 | 자기 평가 |
|------|----------|----------|
| **【GitHub · 기반 코드 실행】** | | |
| 2점 | GitHub URL + commit 5회 이상, 메시지 규칙 준수 | |
| 2점 | 모든 페이지 날짜/시간/학번/성명 표시 | |
| **【기능 구현 – Category 관리】** | | |
| 3점 | Category 목록 + 등록 폼 동작 (GET /categories, GET /categories/create) | |
| 3점 | Category 등록 처리 + Bean Validation 오류 메시지 | |
| 2점 | 동일 이름 중복 등록 방지 (DuplicateCategoryException) | |
| 3점 | Category 삭제 + 연결 상품 있을 때 Flash 오류 메시지 | |
| **【기능 구현 – 상품 검색】** | | |
| 3점 | 상품명 키워드 검색 (JPQL LIKE, 결과 없음 메시지) | |
| 2점 | 카테고리 드롭다운 필터 + 선택 상태 유지 | |
| **【코드 분석 보고서】** | | |
| 5점 | GitHub URL 표지 기재 + 아키텍처 다이어그램 직접 작성 | |
| 5점 | 추가 구현 코드의 동작 원리 본인 이해 기반 서술 | |
| **30점** | **합계** | |
